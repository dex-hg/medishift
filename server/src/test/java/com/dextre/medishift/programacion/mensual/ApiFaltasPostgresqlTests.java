package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Transacciones reales en esquemas privados eliminados al finalizar, sin modificar public. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiFaltasPostgresqlTests extends SoporteApiPlanesMensuales {

	private UUID turno;
	private UUID otroTurno;
	private UUID plan;

	@BeforeEach
	void prepararFaltas() throws Exception {
		inicializar(institucion, actor);
		UUID area = nuevaUnidad(institucion, "F01");
		UUID otraArea = nuevaUnidad(institucion, "F02");
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_institution=?", especialidad, institucion);
		consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", area, consultorio);
		UUID otraSala = UUID.randomUUID();
		consultas.update("""
				INSERT INTO room (id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
				VALUES (?,?,'F02','Otro consultorio','Piso 2',true,'active',?)
				""", otraSala, institucion, otraArea);
		plan = planMensual(area, inicio, inicio.withDayOfMonth(30), 1, "draft");
		turno = nuevoTurno(plan, "draft");
		otroTurno = nuevoTurno(planMensual(otraArea, inicio, inicio.withDayOfMonth(30), 1, "draft"), "draft");
		consultas.update("""
				UPDATE shift SET id_room=?,start_at_shift=start_at_shift+interval '2 hours',end_at_shift=end_at_shift+interval '2 hours'
				WHERE id_shift=?
				""", otraSala, otroTurno);
	}

	@Test
	void presumeAsistenciaSinCrearFilasYPermiteFaltasFuturasSoloEnElTurnoElegido() throws Exception {
		String antes = calendario("2032-11");
		assertEquals("Asistencia", campoTurno(antes, turno, "asistencia"));
		assertEquals(0, cantidadAsistencias());
		String revision = campoTurno(antes, turno, "revisionAsistencia");
		MvcResult resultado = cambiar(turno, revision, true).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.asistencia").value("Falta")).andReturn();
		String despues = calendario("2032-11");
		assertEquals("Falta", campoTurno(despues, turno, "asistencia"));
		assertEquals("Asistencia", campoTurno(despues, otroTurno, "asistencia"));
		assertEquals(2, ((List<?>) JsonPath.read(despues, "$.turnos")).size());
		assertNotEquals(revision, campoTurno(despues, turno, "revisionAsistencia"));
		assertEquals("\"" + campoTurno(despues, turno, "revisionAsistencia") + "\"", resultado.getResponse().getHeader("ETag"));
		assertEquals(1, cantidadAsistencias());
		assertEquals(1, cantidadAuditorias());
		assertEquals(actor, consultas.queryForObject("SELECT id_recorder_user_account FROM attendance", UUID.class));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM attendance WHERE id_shift IN (SELECT id_shift FROM shift WHERE id_schedule=?)", Integer.class, legado));
	}

	@Test
	void restableceAsistenciaRetiraLaExcepcionYConservaAuditoriaDeLaCorreccion() throws Exception {
		String inicial = revision(turno);
		cambiar(turno, inicial, true).andExpect(status().isOk());
		String marcada = revision(turno);
		cambiar(turno, marcada, true).andExpect(status().isOk());
		assertEquals(1, cantidadAuditorias());
		cambiar(turno, marcada, false).andExpect(status().isOk()).andExpect(jsonPath("$.asistencia").value("Asistencia"));
		assertEquals(0, cantidadAsistencias());
		assertEquals(2, cantidadAuditorias());
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM audit_event WHERE entity_name_audit_event='attendance' AND action_audit_event='delete'
				AND before_audit_event->>'status_attendance'='absent' AND after_audit_event IS NULL
				""", Integer.class));
		assertEquals("Asistencia", campoTurno(calendario("2032-11"), turno, "asistencia"));
	}

	@Test
	void tambienRegistraFaltasPasadasSinCambiarLaProgramacion() throws Exception {
		consultas.update("UPDATE schedule SET period_start_schedule='2020-11-01',period_end_schedule='2020-11-30' WHERE id_schedule=?", plan);
		consultas.update("UPDATE shift SET start_at_shift='2020-11-01 08:00-05',end_at_shift='2020-11-01 10:00-05' WHERE id_shift=?", turno);
		String revision = campoTurno(calendario("2020-11"), turno, "revisionAsistencia");
		cambiar(turno, revision, true).andExpect(status().isOk());
		assertEquals("Falta", campoTurno(calendario("2020-11"), turno, "asistencia"));
		assertEquals("draft", consultas.queryForObject("SELECT status_shift FROM shift WHERE id_shift=?", String.class, turno));
	}

	@Test
	void exigeSesionAdministradorYSeparacionEntreInstituciones() throws Exception {
		solicitar(put(ruta(turno)).header("If-Match", condicion(revision(turno))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"falta\":true}")).andExpect(status().isUnauthorized());
		MockHttpSession sinPermiso = sesionDe(institucion, nuevaCuenta(institucion));
		solicitar(put(ruta(turno)).session(sinPermiso).contentType(MediaType.APPLICATION_JSON).content("{\"falta\":true}"))
				.andExpect(status().isForbidden());
		UUID ajena = nuevaInstitucion();
		UUID cuentaAjena = nuevaCuenta(ajena);
		inicializar(ajena, cuentaAjena);
		solicitar(put(ruta(turno)).session(sesionDe(ajena, cuentaAjena)).header("If-Match", condicion(revision(turno)))
				.contentType(MediaType.APPLICATION_JSON).content("{\"falta\":true}")).andExpect(status().isNotFound());
		cambiar(UUID.randomUUID(), revision(turno), true).andExpect(status().isNotFound());
		assertEquals(0, cantidadAsistencias());
	}

	@Test
	void validaBooleanoCamposDesconocidosYRevisionAntesDeEscribir() throws Exception {
		for (String datos : List.of("{}", "{\"falta\":null}", "{\"falta\":\"true\"}", "{\"falta\":true,\"idInstitucion\":\"ajena\"}")) {
			solicitar(put(ruta(turno)).session(sesion).header("If-Match", condicion(revision(turno)))
					.contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isBadRequest());
		}
		solicitar(put(ruta(turno)).session(sesion).contentType(MediaType.APPLICATION_JSON).content("{\"falta\":true}"))
				.andExpect(status().isPreconditionRequired());
		cambiar(turno, "0".repeat(64), true).andExpect(status().isConflict()).andExpect(jsonPath("$.errores.revision").exists());
		assertEquals(0, cantidadAsistencias());
	}

	@Test
	void rechazaRevisionAnteriorTrasCambiarLaAsistenciaOElHorario() throws Exception {
		String anterior = revision(turno);
		cambiar(turno, anterior, true).andExpect(status().isOk());
		cambiar(turno, anterior, false).andExpect(status().isConflict());
		String vigente = revision(otroTurno);
		consultas.update("UPDATE shift SET note_shift='Turno actualizado' WHERE id_shift=?", otroTurno);
		cambiar(otroTurno, vigente, true).andExpect(status().isConflict());
		assertEquals(1, cantidadAsistencias());
	}

	@Test
	void rechazaTurnosCanceladosYSustituidosAunqueConserveSuIdentificador() throws Exception {
		String anterior = revision(turno);
		for (String estado : List.of("cancelled", "superseded")) {
			consultas.update("UPDATE shift SET status_shift=? WHERE id_shift=?", estado, turno);
			cambiar(turno, anterior, true).andExpect(status().isConflict()).andExpect(jsonPath("$.errores.estado").exists());
		}
		assertEquals(0, cantidadAsistencias());
	}

	@Test
	void conservaEntradasYSalidasPreexistentesAlMarcarYCorregir() throws Exception {
		consultas.update("""
				INSERT INTO attendance VALUES (?,?,?,?,'2032-11-01 08:00-05','2032-11-01 10:00-05','present')
				""", UUID.randomUUID(), institucion, turno, actor);
		String marcas = consultas.queryForObject("SELECT check_in_at_attendance::text||check_out_at_attendance::text FROM attendance", String.class);
		cambiar(turno, revision(turno), true).andExpect(status().isOk());
		cambiar(turno, revision(turno), false).andExpect(status().isOk());
		assertEquals(1, cantidadAsistencias());
		assertEquals("present", consultas.queryForObject("SELECT status_attendance FROM attendance", String.class));
		assertEquals(marcas, consultas.queryForObject("SELECT check_in_at_attendance::text||check_out_at_attendance::text FROM attendance", String.class));
	}

	@Test
	void revierteLaFaltaSiFallaSuAuditoria() throws Exception {
		fallarAuditoria("create");
		cambiar(turno, revision(turno), true).andExpect(status().isInternalServerError());
		assertEquals(0, cantidadAsistencias());
		assertEquals(0, cantidadAuditorias());
	}

	@Test
	void dosAdministradoresConLaMismaRevisionNoSeSobrescriben() throws Exception {
		String anterior = revision(turno);
		CountDownLatch salida = new CountDownLatch(1);
		try (var ejecutores = Executors.newFixedThreadPool(2)) {
			var primera = ejecutores.submit(() -> { salida.await(); return cambiar(turno, anterior, true).andReturn().getResponse().getStatus(); });
			var segunda = ejecutores.submit(() -> { salida.await(); return cambiar(turno, anterior, true).andReturn().getResponse().getStatus(); });
			salida.countDown();
			assertEquals(List.of(200, 409), java.util.stream.Stream.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)).sorted().toList());
		}
		assertEquals(1, cantidadAsistencias());
		assertEquals(1, cantidadAuditorias());
	}

	private String calendario(String periodo) throws Exception {
		return solicitar(get("/api/planificador").session(sesion).param("periodo", periodo)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String revision(UUID identificador) throws Exception {
		return campoTurno(calendario("2032-11"), identificador, "revisionAsistencia");
	}

	private String campoTurno(String calendario, UUID identificador, String campo) {
		List<String> valores = JsonPath.read(calendario, "$.turnos[?(@.id == '" + identificador + "')]." + campo);
		return valores.getFirst();
	}

	private ResultActions cambiar(UUID identificador, String revision, boolean falta) throws Exception {
		return solicitar(put(ruta(identificador)).session(sesion).header("If-Match", condicion(revision))
				.contentType(MediaType.APPLICATION_JSON).content("{\"falta\":" + falta + "}"));
	}

	private String ruta(UUID identificador) { return "/api/planificador/turnos/" + identificador + "/asistencia"; }
	private String condicion(String revision) { return "\"" + revision + "\""; }
	private int cantidadAsistencias() { return consultas.queryForObject("SELECT count(*) FROM attendance", Integer.class); }
	private int cantidadAuditorias() { return consultas.queryForObject("SELECT count(*) FROM audit_event WHERE entity_name_audit_event='attendance'", Integer.class); }
}
