package com.dextre.medishift.programacion.mensual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Las transacciones se confirman en esquemas privados que se eliminan tras cada caso. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiConfiguracionPostgresqlTests extends SoporteApiPlanesMensuales {

	@Autowired private PasswordEncoder codificador;
	private UUID area;

	@BeforeEach
	void prepararConfiguracion() throws Exception {
		inicializar(institucion, actor);
		area = nuevaUnidad(institucion, "CONFIG");
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_institution=?", especialidad, institucion);
		consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", area, consultorio);
	}

	@Test
	void consultaValoresAnterioresSinCrearFilasYProtegeLaCache() throws Exception {
		solicitar(get("/api/configuracion").session(sesion)).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store")).andExpect(header().exists("ETag"))
				.andExpect(jsonPath("$.minutosDiarios").value(360)).andExpect(jsonPath("$.minutosSemanales").value(2160));
		solicitar(get("/api/cuenta").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("")).andExpect(jsonPath("$.esAdministrador").value(true))
				.andExpect(jsonPath("$.preferencias.horaInicio").value("08:00")).andExpect(jsonPath("$.preferencias.horaFin").value("12:00"))
				.andExpect(jsonPath("$.password_hash_user_account").doesNotExist());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM institution_settings", Integer.class));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM account_settings", Integer.class));
	}

	@Test
	void persisteReglasLasEntregaAlPlanificadorYLasAplicaAlGuardarTurnos() throws Exception {
		guardarReglas(480, 2400, 450).andExpect(status().isOk());
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.politica.minutosDiarios").value(480))
				.andExpect(jsonPath("$.feriados.minutosPorFeriado").value(450));
		crearTurno("2032-11-02", "08:00", "16:00", true).andExpect(status().isCreated());
		crearTurno("2032-11-03", "08:00", "16:01", true).andExpect(status().isConflict());
		crearTurno("2032-11-08", "08:00", "16:00", false).andExpect(status().isCreated());
		assertEquals(480, consultas.queryForObject("SELECT daily_minutes FROM institution_settings WHERE id_institution=?", Integer.class, institucion));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE entity_name_audit_event='institution_settings'", Integer.class));
	}

	@Test
	void limiteSemanalPersonalizadoSumaTurnosMensualesYSemanales() throws Exception {
		guardarReglas(480, 600, 360).andExpect(status().isOk());
		// La semana ya contiene dos horas aprobadas el lunes en el fixture.
		crearTurno("2032-11-02", "08:00", "16:00", true).andExpect(status().isCreated());
		crearTurno("2032-11-03", "08:00", "08:01", true).andExpect(status().isConflict());
		crearTurno("2032-11-08", "08:00", "16:00", false).andExpect(status().isCreated());
		crearTurno("2032-11-09", "08:00", "10:00", true).andExpect(status().isCreated());
		crearTurno("2032-11-10", "08:00", "08:01", false).andExpect(status().isConflict());
	}

	@Test
	void reduceSoloSiLosTurnosVigentesCumplenYNoCambiaLaZonaConHistorial() throws Exception {
		guardarReglas(60, 420, 360).andExpect(status().isConflict());
		String actual = revision("/api/configuracion");
		guardar("/api/configuracion", reglas(360, 2160, 360).replace("America/Lima", "America/Bogota"), actual)
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.zonaHoraria").exists());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM institution_settings", Integer.class));
	}

	@Test
	void soloAdministradorModificaReglasYNoAdmiteSuplantarInstitucionOActor() throws Exception {
		UUID usuario = nuevaCuenta(institucion);
		MockHttpSession otraSesion = sesionDe(institucion, usuario);
		solicitar(put("/api/configuracion").session(otraSesion).header("If-Match", revision("/api/configuracion"))
				.contentType(MediaType.APPLICATION_JSON).content(reglas(480, 2400, 360))).andExpect(status().isForbidden());
		guardar("/api/cuenta/perfil", "{\"nombre\":\"Temporal\",\"idCuenta\":\"" + usuario + "\"}", revision("/api/cuenta"))
				.andExpect(status().isBadRequest());
		solicitar(get("/api/cuenta")).andExpect(status().isUnauthorized());
		solicitar(put("/api/cuenta/perfil").session(sesion).header("Origin", "https://ajeno.invalid")
				.header("If-Match", revision("/api/cuenta")).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Temporal\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void perfilYPreferenciasPersistenSonPrivadosYRechazanRevisionesAntiguas() throws Exception {
		String anterior = revision("/api/cuenta");
		guardar("/api/cuenta/perfil", "{\"nombre\":\"Nombre de ensayo\"}", anterior).andExpect(status().isOk());
		guardar("/api/cuenta/perfil", "{\"nombre\":\"Perdido\"}", anterior).andExpect(status().isConflict());
		guardar("/api/cuenta/preferencias", "{\"barraCompacta\":true,\"horaInicio\":\"09:00\",\"horaFin\":\"13:30\"}", revision("/api/cuenta"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nombre de ensayo"));
		solicitar(get("/api/cuenta").session(sesionDe(institucion, actor))).andExpect(status().isOk())
				.andExpect(jsonPath("$.preferencias.barraCompacta").value(true)).andExpect(jsonPath("$.preferencias.horaFin").value("13:30"));
		UUID otro = nuevaCuenta(institucion);
		solicitar(get("/api/cuenta").session(sesionDe(institucion, otro))).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("")).andExpect(jsonPath("$.preferencias.barraCompacta").value(false));
		UUID otraInstitucion = nuevaInstitucion();
		UUID otraCuenta = nuevaCuenta(otraInstitucion);
		guardarReglas(480, 2400, 450).andExpect(status().isOk());
		solicitar(get("/api/configuracion").session(sesionDe(otraInstitucion, otraCuenta))).andExpect(status().isOk())
				.andExpect(jsonPath("$.minutosDiarios").value(360));
	}

	@Test
	void validaCamposRangosHorasYCondicionesAntesDeEscribir() throws Exception {
		for (String datos : List.of(reglas(0, 2160, 360), reglas(1441, 2160, 360), reglas(360, 359, 360),
				reglas(360, 2521, 360), reglas(360, 2160, -1), reglas(360, 2160, 1441),
				reglas(360, 2160, 360).replace("360", "360.5"))) {
			guardar("/api/configuracion", datos, revision("/api/configuracion")).andExpect(status().isBadRequest());
		}
		for (String datos : List.of("{\"barraCompacta\":\"true\",\"horaInicio\":\"08:00\",\"horaFin\":\"12:00\"}",
				"{\"barraCompacta\":true,\"horaInicio\":\"12:00\",\"horaFin\":\"08:00\"}",
				"{\"barraCompacta\":true,\"horaInicio\":\"24:00\",\"horaFin\":\"25:00\"}")) {
			guardar("/api/cuenta/preferencias", datos, revision("/api/cuenta")).andExpect(status().isBadRequest());
		}
		guardar("/api/cuenta/preferencias", "{\"barraCompacta\":true,\"horaInicio\":\"08:00\",\"horaFin\":\"15:00\"}", revision("/api/cuenta"))
				.andExpect(status().isConflict());
		solicitar(put("/api/cuenta/perfil").session(sesion).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ensayo\"}"))
				.andExpect(status().isPreconditionRequired());
		guardarReglas(360, 2160, 0).andExpect(status().isOk()).andExpect(jsonPath("$.minutosPorFeriado").value(0));
	}

	@Test
	void cambiaClaveConVerificacionYNoGuardaSecretosEnAuditoria() throws Exception {
		String anterior = "Clave anterior de ensayo";
		String nueva = "Clave nueva de ensayo 2032";
		consultas.update("UPDATE user_account SET password_hash_user_account=? WHERE id_user_account=?", codificador.encode(anterior), actor);
		String revision = revision("/api/cuenta");
		guardar("/api/cuenta/contrasena", "{\"contrasenaActual\":\"incorrecta\",\"contrasenaNueva\":\"" + nueva + "\"}", revision)
				.andExpect(status().isConflict());
		guardar("/api/cuenta/contrasena", "{\"contrasenaActual\":\"" + anterior + "\",\"contrasenaNueva\":\"" + nueva + "\"}", revision)
				.andExpect(status().isOk()).andExpect(jsonPath("$.contrasenaNueva").doesNotExist());
		String hash = consultas.queryForObject("SELECT password_hash_user_account FROM user_account WHERE id_user_account=?", String.class, actor);
		assertTrue(codificador.matches(nueva, hash));
		assertFalse(codificador.matches(anterior, hash));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE action_audit_event='change_password' AND before_audit_event IS NULL AND after_audit_event='{\"contrasenaActualizada\":true}'::jsonb", Integer.class));
		String codigo = consultas.queryForObject("SELECT code_institution FROM institution WHERE id_institution=?", String.class, institucion);
		String credenciales = "{\"codigoInstitucion\":\"" + codigo + "\",\"correo\":\"ensayo@medishift.test\",\"contrasena\":\"";
		solicitar(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content(credenciales + anterior + "\"}"))
				.andExpect(status().isUnauthorized());
		solicitar(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content(credenciales + nueva + "\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void dosEscriturasConLaMismaRevisionNoSeSobrescriben() throws Exception {
		String revision = revision("/api/configuracion");
		CountDownLatch comenzar = new CountDownLatch(1);
		try (var ejecutor = Executors.newFixedThreadPool(2)) {
			var primera = ejecutor.submit(() -> { comenzar.await(); return guardar("/api/configuracion", reglas(480, 2400, 450), revision).andReturn().getResponse().getStatus(); });
			var segunda = ejecutor.submit(() -> { comenzar.await(); return guardar("/api/configuracion", reglas(420, 2400, 360), revision).andReturn().getResponse().getStatus(); });
			comenzar.countDown();
			assertEquals(List.of(200, 409), java.util.stream.Stream.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)).sorted().toList());
		}
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE entity_name_audit_event='institution_settings'", Integer.class));
	}

	@Test
	void elFalloDeAuditoriaRevierteLaEscrituraYLaRevision() throws Exception {
		consultas.execute("""
				CREATE FUNCTION impedir_configuracion() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN IF NEW.entity_name_audit_event='institution_settings' THEN RAISE EXCEPTION 'Ensayo de reversión'; END IF; RETURN NEW; END; $$;
				CREATE TRIGGER impedir_configuracion BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION impedir_configuracion();
				""");
		String antes = revision("/api/configuracion");
		guardarReglas(480, 2400, 450).andExpect(status().isInternalServerError());
		assertEquals(antes, revision("/api/configuracion"));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM institution_settings", Integer.class));
	}

	@Test
	void migracionEsIdempotenteYConservaRestricciones() throws Exception {
		String sql = Files.readString(Path.of("../db/migraciones/V004__configuracion_cuenta_institucion.sql"));
		consultas.execute(sql); consultas.execute(sql);
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM account_settings", Integer.class));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=?::regnamespace AND conname='account_settings_same_tenant'", Integer.class, esquema));
	}

	private String reglas(int diarios, int semanales, int feriados) {
		return "{\"nombreInstitucion\":\"Ensayo aislado\",\"zonaHoraria\":\"America/Lima\",\"minutosDiarios\":" + diarios
				+ ",\"minutosSemanales\":" + semanales + ",\"minutosPorFeriado\":" + feriados + ",\"autoprogramacionMedica\":false}";
	}

	private String revision(String ruta) throws Exception {
		return solicitar(get(ruta).session(sesion)).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
	}

	private ResultActions guardar(String ruta, String datos, String revision) throws Exception {
		return solicitar(put(ruta).session(sesion).header("If-Match", revision).contentType(MediaType.APPLICATION_JSON).content(datos));
	}

	private ResultActions guardarReglas(int diarios, int semanales, int feriados) throws Exception {
		return guardar("/api/configuracion", reglas(diarios, semanales, feriados), revision("/api/configuracion"));
	}

	private ResultActions crearTurno(String fecha, String inicioTurno, String finTurno, boolean mensual) throws Exception {
		String datos = "{\"idProfesional\":\"" + profesional + "\",\"idConsultorio\":\"" + consultorio
				+ "\",\"idEspecialidad\":\"" + especialidad + "\",\"fecha\":\"" + fecha + "\",\"horaInicio\":\""
				+ inicioTurno + "\",\"horaFin\":\"" + finTurno + "\",\"observacion\":\"\""
				+ (mensual ? ",\"idArea\":\"" + area + "\"" : "") + "}";
		return solicitar(post(mensual ? "/api/planificador/turnos" : "/api/horarios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos));
	}
}
