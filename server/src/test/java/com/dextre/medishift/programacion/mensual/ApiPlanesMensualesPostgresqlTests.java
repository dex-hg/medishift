package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiPlanesMensualesPostgresqlTests extends SoporteApiPlanesMensuales {

	@BeforeEach
	void habilitarAdministrador() throws Exception {
		inicializar(institucion, actor);
	}

	@Test
	void creaUnidadYMesCompletoYLosConsultaConRevisionYAuditoriaReal() throws Exception {
		UUID unidad = crearUnidad(" U01 ");
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
				.andExpect(jsonPath("$.unidades[0].codigo").value("U01"));
		MvcResult creado = solicitar(post("/api/planes-mensuales").session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(plan(unidad, "2032-02")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.fechaInicio").value("2032-02-01"))
				.andExpect(jsonPath("$.fechaFin").value("2032-02-29"))
				.andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
				.andExpect(jsonPath("$.idCreador").value(actor.toString()))
				.andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.estado").value("Borrador")).andReturn();
		String identificador = JsonPath.read(creado.getResponse().getContentAsString(), "$.id");
		String revision = JsonPath.read(creado.getResponse().getContentAsString(), "$.revision");
		assertEquals("/api/planes-mensuales/" + identificador, creado.getResponse().getHeader("Location"));
		assertEquals("\"" + revision + "\"", creado.getResponse().getHeader("ETag"));
		solicitar(get("/api/planes-mensuales/" + identificador).session(sesion)).andExpect(status().isOk())
				.andExpect(header().string("ETag", creado.getResponse().getHeader("ETag")));
		solicitar(get("/api/planes-mensuales").session(sesion).param("unidad", unidad.toString()).param("periodo", "2032-02"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.planes.length()").value(1));
		solicitar(get("/api/planes-mensuales").session(sesion).param("unidad", unidad.toString()).param("periodo", "2032-03"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.planes.length()").value(0));
		assertEquals(2, consultas.queryForObject("""
				SELECT count(*) FROM audit_event WHERE action_audit_event = 'create'
				AND id_actor_user_account = ? AND id_institution = ? AND before_audit_event IS NULL
				AND after_audit_event IS NOT NULL
				""", Integer.class, actor, institucion));
		assertFalse(consultas.queryForObject("SELECT string_agg(after_audit_event::text, '') FROM audit_event",
				String.class).matches("(?is).*(password|contrasena|cookie|token|hash-privado).*"));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE id_schedule = ? AND period_type_schedule = 'weekly'",
				Integer.class, legado));
	}

	@Test
	void institucionesNoPuedenLeerNiReferenciarLasUnidadesYPlanesAjenos() throws Exception {
		UUID unidad = crearUnidad("U01");
		String identificador = JsonPath.read(solicitar(post("/api/planes-mensuales").session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(plan(unidad, "2032-11")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		UUID otraInstitucion = nuevaInstitucion();
		UUID otraCuenta = nuevaCuenta(otraInstitucion);
		inicializar(otraInstitucion, otraCuenta);
		var otraSesion = sesionDe(otraInstitucion, otraCuenta);
		solicitar(get("/api/unidades-planificacion").session(otraSesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.unidades.length()").value(0));
		solicitar(get("/api/planes-mensuales/" + identificador).session(otraSesion)).andExpect(status().isNotFound());
		solicitar(get("/api/planes-mensuales/" + legado).session(sesion)).andExpect(status().isNotFound());
		solicitar(post("/api/planes-mensuales").session(otraSesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-11"))).andExpect(status().isNotFound());
		solicitar(get("/api/planes-mensuales").session(otraSesion).param("unidad", unidad.toString()).param("periodo", "2032-11"))
				.andExpect(status().isNotFound());
	}

	@Test
	void exigeSesionYAdministradorYRevalidaLaRevocacionSinCerrarSesion() throws Exception {
		solicitar(get("/api/unidades-planificacion")).andExpect(status().isUnauthorized());
		UUID sinRol = nuevaCuenta(institucion);
		solicitar(get("/api/unidades-planificacion").session(sesionDe(institucion, sinRol))).andExpect(status().isForbidden());
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isOk());
		consultas.update("DELETE FROM user_role WHERE id_user_account = ?", actor);
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isForbidden());
		assertFalse(sesion.isInvalid());
	}

	@Test
	void cuentaOInstitucionInactivaInvalidanLaSesion() throws Exception {
		consultas.update("UPDATE user_account SET status_user_account = 'inactive' WHERE id_user_account = ?", actor);
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isUnauthorized());
		assertTrue(sesion.isInvalid());
		consultas.update("UPDATE user_account SET status_user_account = 'active' WHERE id_user_account = ?", actor);
		consultas.update("UPDATE institution SET status_institution = 'inactive' WHERE id_institution = ?", institucion);
		var nuevaSesion = sesionDe(institucion, actor);
		solicitar(get("/api/unidades-planificacion").session(nuevaSesion)).andExpect(status().isUnauthorized());
		assertTrue(nuevaSesion.isInvalid());
	}

	@Test
	void unEsquemaSinNucleoMensualRespondeNoDisponibleSinEscribir() throws Exception {
		consultas.execute("ALTER TABLE planning_unit RENAME TO planning_unit_no_instalada");
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isServiceUnavailable());
		solicitar(post("/api/unidades-planificacion").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(unidad("U01"))).andExpect(status().isServiceUnavailable());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM planning_unit_no_instalada", Integer.class));
		assertEquals(1, contar("audit_event"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "null", "[]", "{", "{\"codigo\":\"U\",\"nombre\":\"Unidad\",\"tipo\":\"Servicio\",\"rol\":\"administrador\"}",
			"{\"codigo\":\"U\",\"nombre\":\"Unidad\",\"tipo\":\"Desconocido\"}",
			"{\"codigo\":0,\"nombre\":\"Unidad\",\"tipo\":\"Servicio\"}",
			"{\"codigo\":\"U\",\"nombre\":\"linea\\ncontrol\",\"tipo\":\"Servicio\"}"})
	void rechazaJsonOCamposDeUnidadInvalidosSinGuardarNiAuditar(String contenido) throws Exception {
		solicitar(post("/api/unidades-planificacion").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isBadRequest());
		assertEquals(0, contar("planning_unit"));
		assertEquals(1, contar("audit_event"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2032-2", "2032-13", "0000-01", "10000-01", "2032-02-01", "", " 2032-02"})
	void rechazaMesesInvalidosYConservaElHistoricoSemanal(String periodo) throws Exception {
		UUID unidad = crearUnidad("U01");
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, periodo))).andExpect(status().isBadRequest());
		assertEquals(1, contar("schedule"));
		assertEquals(2, contar("audit_event"));
	}

	@Test
	void rechazaCamposControladosPorElServidorYReferenciasInvalidas() throws Exception {
		UUID unidad = crearUnidad("U01");
		for (String campo : List.of("idInstitucion", "idCreador", "estado", "version", "rol")) {
			String datos = plan(unidad, "2032-11").replace("}", ",\"" + campo + "\":\"inventado\"}");
			solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores." + campo).exists());
		}
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idUnidad\":\"1-1-1-1-1\",\"periodo\":\"2032-11\"}")).andExpect(status().isBadRequest());
		solicitar(get("/api/planes-mensuales").session(sesion)).andExpect(status().isBadRequest());
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(UUID.randomUUID(), "2032-11"))).andExpect(status().isNotFound());
		assertEquals(1, contar("schedule"));
	}

	@Test
	void duplicadosUnidadInactivaYOrigenAjenoNoCreanDatosNuevos() throws Exception {
		UUID unidad = crearUnidad("U01");
		solicitar(post("/api/unidades-planificacion").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(unidad("U01"))).andExpect(status().isConflict());
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-11"))).andExpect(status().isCreated());
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-11"))).andExpect(status().isConflict());
		consultas.update("UPDATE planning_unit SET status_planning_unit = 'inactive' WHERE id_planning_unit = ?", unidad);
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-12"))).andExpect(status().isConflict());
		solicitar(get("/api/planes-mensuales").session(sesion).param("unidad", unidad.toString()).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.planes.length()").value(1));
		solicitar(post("/api/unidades-planificacion").session(sesion).header("Origin", "https://ajeno.test")
				.contentType(MediaType.APPLICATION_JSON).content(unidad("U02"))).andExpect(status().isForbidden());
		assertEquals(1, contar("planning_unit"));
		assertEquals(2, contar("schedule"));
		assertEquals(3, contar("audit_event"));
	}

	@Test
	void unFalloRealDeAuditoriaRevierteTantoUnidadComoPlan() throws Exception {
		UUID unidad = crearUnidad("U01");
		fallarAuditoria("create");
		solicitar(post("/api/unidades-planificacion").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(unidad("U02"))).andExpect(status().isInternalServerError());
		solicitar(post("/api/planes-mensuales").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-11"))).andExpect(status().isInternalServerError());
		assertEquals(1, contar("planning_unit"));
		assertEquals(1, contar("schedule"));
		assertEquals(2, contar("audit_event"));
	}

	@Test
	void dosCreacionesConcurrentesDelMismoMesConfirmanUnaSolaCabecera() throws Exception {
		UUID unidad = crearUnidad("U01");
		CountDownLatch inicioConcurrente = new CountDownLatch(1);
		var ejecutor = Executors.newFixedThreadPool(2);
		try {
			var primera = ejecutor.submit(() -> crearAlMismoTiempo(unidad, inicioConcurrente));
			var segunda = ejecutor.submit(() -> crearAlMismoTiempo(unidad, inicioConcurrente));
			inicioConcurrente.countDown();
			assertEquals(List.of(201, 409), List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS))
					.stream().sorted().toList());
			assertEquals(2, contar("schedule"));
			assertEquals(3, contar("audit_event"));
		} finally {
			ejecutor.shutdownNow();
			assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private int crearAlMismoTiempo(UUID unidad, CountDownLatch comienzo) throws Exception {
		assertTrue(comienzo.await(10, TimeUnit.SECONDS));
		return solicitar(post("/api/planes-mensuales").session(sesionDe(institucion, actor)).contentType(MediaType.APPLICATION_JSON)
				.content(plan(unidad, "2032-11"))).andReturn().getResponse().getStatus();
	}

	private UUID crearUnidad(String codigo) throws Exception {
		String contenido = solicitar(post("/api/unidades-planificacion").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(unidad(codigo))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(contenido, "$.id"));
	}

	private String unidad(String codigo) {
		return "{\"codigo\":\"" + codigo + "\",\"nombre\":\"Servicio de ensayo\",\"tipo\":\"Servicio\"}";
	}

	private String plan(UUID unidad, String periodo) {
		return "{\"idUnidad\":\"" + unidad + "\",\"periodo\":\"" + periodo + "\"}";
	}
}
