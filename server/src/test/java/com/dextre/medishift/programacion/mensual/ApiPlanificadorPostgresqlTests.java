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
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Las escrituras se confirman o revierten en PostgreSQL, dentro de un esquema exclusivo por prueba. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiPlanificadorPostgresqlTests extends SoporteApiPlanesMensuales {

	private UUID area;
	private UUID otraArea;
	private UUID otroConsultorio;
	private UUID otroProfesional;

	@BeforeEach
	void prepararCalendario() throws Exception {
		inicializar(institucion, actor);
		area = nuevaUnidad(institucion, "DER");
		otraArea = nuevaUnidad(institucion, "ODO");
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_institution=?", especialidad, institucion);
		consultas.update("UPDATE room SET id_planning_unit = ? WHERE id_room = ?", area, consultorio);
		otroConsultorio = UUID.randomUUID();
		consultas.update("""
				INSERT INTO room (id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
				VALUES (?,?,'R02','Otra sala','Piso 2',true,'active',?)
				""", otroConsultorio, institucion, otraArea);
		otroProfesional = UUID.randomUUID();
		consultas.update("""
				INSERT INTO professional (id_professional,id_institution,first_name_professional,last_name_professional,
				license_number_professional,category_professional,email_professional,status_professional)
				VALUES (?,?,'Otra','Persona','P02','Medico','otra@medishift.test','active')
				""", otroProfesional, institucion);
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,true)", otroProfesional, especialidad);
	}

	@Test
	void creaConsultaMueveYEliminaUnTurnoMensualSinAlterarElSemanal() throws Exception {
		MvcResult creado = crear(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"));
		String identificador = campo(creado, "id");
		String plan = campo(creado, "idPlan");
		assertEquals(area.toString(), campo(creado, "idArea"));
		assertEquals("Borrador", campo(creado, "estado"));
		assertEquals("\"" + campo(creado, "revision") + "\"", creado.getResponse().getHeader("ETag"));
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
				.andExpect(jsonPath("$.areas.length()").value(2))
				.andExpect(jsonPath("$.profesionales.length()").value(2))
				.andExpect(jsonPath("$.profesionales[?(@.id == '" + otroProfesional + "')].colegiatura").value(org.hamcrest.Matchers.contains("P02")))
				.andExpect(jsonPath("$.planes.length()").value(1))
				.andExpect(jsonPath("$.turnos[0].fecha").value("2032-11-02"))
				.andExpect(jsonPath("$.turnos[0].idPlan").value(plan));
		MvcResult movido = solicitar(put("/api/planificador/turnos/" + identificador).session(sesion)
				.header("If-Match", creado.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
				.content(datos(otraArea, profesional, otroConsultorio, "2032-12-02", "09:00", "11:00")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.idArea").value(otraArea.toString()))
				.andExpect(jsonPath("$.fecha").value("2032-12-02")).andReturn();
		assertNotEquals(plan, campo(movido, "idPlan"));
		assertNotEquals(campo(creado, "revision"), campo(movido, "revision"));
		solicitar(delete("/api/planificador/turnos/" + identificador).session(sesion)
				.header("If-Match", movido.getResponse().getHeader("ETag"))).andExpect(status().isNoContent());
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='weekly'", Integer.class));
		assertEquals(3, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE entity_name_audit_event='shift'", Integer.class));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE action_audit_event='delete'"
				+ " AND before_audit_event IS NOT NULL AND after_audit_event IS NULL", Integer.class));
	}

	@Test
	void reutilizaUnaCabeceraYRechazaCrucesEntreAreasYConSemanas() throws Exception {
		crear(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"));
		crear(datos(area, profesional, consultorio, "2032-11-03", "08:00", "10:00"));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
		rechazar(datos(otraArea, profesional, otroConsultorio, "2032-11-02", "09:00", "11:00"), 409, "idProfesional");
		rechazar(datos(area, otroProfesional, consultorio, "2032-11-02", "09:00", "11:00"), 409, "idConsultorio");
		rechazar(datos(area, profesional, consultorio, "2032-11-01", "09:00", "11:00"), 409, "idProfesional");
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
		crear(datos(area, otroProfesional, consultorio, "2032-11-02", "10:00", "12:00"));
	}

	@Test
	void rutasSemanalesHeredadasRespetanLosBorradoresMensuales() throws Exception {
		crear(datos(area, profesional, consultorio, "2032-11-08", "08:00", "10:00"));
		String semanal = datos(area, profesional, consultorio, "2032-11-08", "09:00", "11:00")
				.replace("\"idArea\":\"" + area + "\",", "");
		solicitar(post("/api/horarios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(semanal))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idProfesional").exists());
		MvcResult creado = solicitar(post("/api/horarios").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(semanal.replace("09:00", "10:00").replace("11:00", "12:00")))
				.andExpect(status().isCreated()).andReturn();
		solicitar(put("/api/horarios/" + campo(creado, "id")).session(sesion)
				.header("If-Match", creado.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content(semanal))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idProfesional").exists());
		// Simula un solapamiento preexistente: la aprobación también debe volver a validar.
		consultas.update("UPDATE shift SET start_at_shift='2032-11-08 09:00-05'::timestamptz WHERE id_shift=?",
				UUID.fromString(campo(creado, "id")));
		String semana = solicitar(get("/api/horarios").session(sesion).param("fechaInicio", "2032-11-08"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String aprobacion = "{\"fechaInicio\":\"2032-11-08\",\"idHorario\":\""
				+ JsonPath.read(semana, "$.idHorario") + "\",\"revision\":\"" + JsonPath.read(semana, "$.revision") + "\"}";
		solicitar(post("/api/horarios/aprobar").session(sesion).contentType(MediaType.APPLICATION_JSON).content(aprobacion))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idProfesional").exists());
	}

	@Test
	void impideMoverAPlanAprobadoYModificarPlanAprobado() throws Exception {
		MvcResult creado = crear(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"));
		planMensual(otraArea, inicio, inicio.withDayOfMonth(30), 1, "approved");
		solicitar(put("/api/planificador/turnos/" + campo(creado, "id")).session(sesion)
				.header("If-Match", creado.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
				.content(datos(otraArea, profesional, otroConsultorio, "2032-11-03", "08:00", "10:00")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.estado").exists());
		consultas.update("UPDATE schedule SET status_schedule='approved',id_approver_user_account=?,approved_at_schedule=now() WHERE id_schedule=?",
				actor, UUID.fromString(campo(creado, "idPlan")));
		String calendario = solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String revision = JsonPath.read(calendario, "$.turnos[0].revision");
		solicitar(delete("/api/planificador/turnos/" + campo(creado, "id")).session(sesion)
				.header("If-Match", "\"" + revision + "\"")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errores.estado").exists());
	}

	@Test
	void exigeRevisionYRechazaEdicionesObsoletasSinCambiarElTurno() throws Exception {
		String datos = datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00");
		MvcResult creado = crear(datos);
		String ruta = "/api/planificador/turnos/" + campo(creado, "id");
		solicitar(put(ruta).session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isPreconditionRequired());
		solicitar(delete(ruta).session(sesion)).andExpect(status().isPreconditionRequired());
		solicitar(put(ruta).session(sesion).header("If-Match", campo(creado, "revision"))
				.contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isBadRequest());
		solicitar(delete(ruta).session(sesion).header("If-Match", "\"" + "0".repeat(64) + "\""))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.revision").exists());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
	}

	@Test
	void creaConsultorioEnSuCarpetaYConservaVinculosHistoricos() throws Exception {
		String formulario = "{\"idArea\":\"" + area + "\",\"codigo\":\"R03\",\"nombre\":\"Sala nueva\",\"ubicacion\":\"Piso 1\"}";
		solicitar(post("/api/planificador/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(formulario))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.idArea").value(area.toString()))
				.andExpect(jsonPath("$.usoGeneral").value(false))
				.andExpect(jsonPath("$.idEspecialidadArea").value(especialidad.toString()));
		solicitar(post("/api/planificador/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(formulario))
				.andExpect(status().isConflict());
		solicitar(put("/api/planificador/consultorios/" + consultorio + "/area").session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content("{\"idArea\":\"" + otraArea + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.idArea").value(otraArea.toString()));
		crear(datos(otraArea, profesional, consultorio, "2032-11-02", "08:00", "10:00"));
		solicitar(put("/api/planificador/consultorios/" + consultorio + "/area").session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content("{\"idArea\":\"" + area + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idArea").exists());
		assertEquals(otraArea, consultas.queryForObject("SELECT id_planning_unit FROM room WHERE id_room=?", UUID.class, consultorio));
	}

	@Test
	void aplicaLimitesDiariosYSemanalesIncluyendoElMesVecino() throws Exception {
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "15:00"), 409, "horaFin");
		crear(datos(area, profesional, consultorio, "2032-11-02", "08:00", "12:00"));
		rechazar(datos(otraArea, profesional, otroConsultorio, "2032-11-02", "12:00", "15:00"), 409, "horaFin");
		// La semana del 29 de noviembre atraviesa dos cabeceras mensuales.
		for (String fecha : List.of("2032-11-29", "2032-11-30", "2032-12-01", "2032-12-02", "2032-12-03", "2032-12-04")) {
			crear(datos(area, profesional, consultorio, fecha, "08:00", "14:00"));
		}
		rechazar(datos(otraArea, profesional, otroConsultorio, "2032-12-05", "08:00", "09:00"), 409, "horaFin");
	}

	@Test
	void validaActivosCarpetaEspecialidadFechaYNocturnos() throws Exception {
		rechazar(datos(otraArea, profesional, consultorio, "2032-11-02", "08:00", "10:00"), 409, "idConsultorio");
		rechazar(datos(area, profesional, consultorio, "2001-11-02", "08:00", "10:00"), 409, "fecha");
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "22:00", "06:00"), 400, "horaFin");
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00").replace(especialidad.toString(), UUID.randomUUID().toString()), 409, "idEspecialidad");
		consultas.update("UPDATE professional SET status_professional='inactive' WHERE id_professional=?", profesional);
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"), 409, "idProfesional");
		consultas.update("UPDATE professional SET status_professional='active' WHERE id_professional=?", profesional);
		consultas.update("UPDATE room SET status_room='inactive' WHERE id_room=?", consultorio);
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"), 409, "idConsultorio");
		consultas.update("UPDATE room SET status_room='active' WHERE id_room=?", consultorio);
		consultas.update("UPDATE planning_unit SET status_planning_unit='inactive' WHERE id_planning_unit=?", area);
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"), 409, "idArea");
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
	}

	@Test
	void ausenciasYBloqueosSeRespetanSinExigirDisponibilidad() throws Exception {
		consultas.update("""
				INSERT INTO professional_leave VALUES (?,?,'2032-11-02 08:00-05'::timestamptz,
				'2032-11-02 12:00-05'::timestamptz,'Permiso de ensayo','approved')
				""", UUID.randomUUID(), profesional);
		rechazar(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"), 409, "idProfesional");
		consultas.update("""
				INSERT INTO room_block VALUES (?,?,'2032-11-03 08:00-05'::timestamptz,
				'2032-11-03 12:00-05'::timestamptz,'Mantenimiento de ensayo','active')
				""", UUID.randomUUID(), consultorio);
		rechazar(datos(area, profesional, consultorio, "2032-11-03", "08:00", "10:00"), 409, "idConsultorio");
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM availability", Integer.class));
		crear(datos(area, profesional, consultorio, "2032-11-04", "08:00", "10:00"));
	}

	@Test
	void migracionImpideCarpetasAjenasYReversionConDatosVinculados() throws Exception {
		UUID ajena = nuevaUnidad(nuevaInstitucion(), "AJENA");
		conexion.setAutoCommit(false);
		try {
			rechaza("23503", () -> consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", ajena, consultorio));
			var punto = conexion.setSavepoint();
			assertThrows(org.springframework.dao.DataAccessException.class,
					() -> ejecutarArchivo("U002__retirar_consultorios_por_area.sql"));
			conexion.rollback(punto);
			assertEquals(area, consultas.queryForObject("SELECT id_planning_unit FROM room WHERE id_room=?", UUID.class, consultorio));
		} finally {
			conexion.rollback();
			conexion.setAutoCommit(true);
		}
	}

	@Test
	void protegeSesionRolOrigenYDatosDeOtrasInstituciones() throws Exception {
		solicitar(get("/api/planificador").param("periodo", "2032-11")).andExpect(status().isUnauthorized());
		UUID sinRol = nuevaCuenta(institucion);
		solicitar(get("/api/planificador").session(sesionDe(institucion, sinRol)).param("periodo", "2032-11"))
				.andExpect(status().isForbidden());
		String datos = datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00");
		solicitar(post("/api/planificador/turnos").session(sesion).header("Origin", "https://ajeno.test")
				.contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isForbidden());
		MvcResult creado = crear(datos);
		UUID otraInstitucion = nuevaInstitucion();
		UUID otraCuenta = nuevaCuenta(otraInstitucion);
		inicializar(otraInstitucion, otraCuenta);
		var otraSesion = sesionDe(otraInstitucion, otraCuenta);
		solicitar(get("/api/planificador").session(otraSesion).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.areas.length()").value(0))
				.andExpect(jsonPath("$.turnos.length()").value(0));
		solicitar(post("/api/planificador/turnos").session(otraSesion).contentType(MediaType.APPLICATION_JSON).content(datos))
				.andExpect(status().isNotFound());
		solicitar(delete("/api/planificador/turnos/" + campo(creado, "id")).session(otraSesion)
				.header("If-Match", creado.getResponse().getHeader("ETag"))).andExpect(status().isNotFound());
		consultas.update("DELETE FROM user_role WHERE id_user_account=?", actor);
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11")).andExpect(status().isForbidden());
	}

	@Test
	void revierteCreacionAutomaticaDePlanCuandoFallaLaAuditoriaDelTurno() throws Exception {
		consultas.execute("""
				CREATE FUNCTION fallo_auditoria_turno() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
				IF NEW.entity_name_audit_event='shift' THEN RAISE EXCEPTION 'Fallo controlado'; END IF; RETURN NEW; END; $$;
				CREATE TRIGGER fallo_auditoria_turno BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION fallo_auditoria_turno();
				""");
		solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00")))
				.andExpect(status().isInternalServerError());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
		assertEquals(1, contar("audit_event"));
	}

	@Test
	void auditoriaFallidaRevierteEdicionEliminacionYCreacionDeConsultorio() throws Exception {
		MvcResult creado = crear(datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00"));
		String ruta = "/api/planificador/turnos/" + campo(creado, "id");
		String revision = creado.getResponse().getHeader("ETag");
		fallarAuditoria("create");
		// La creación de consultorio debe revertirse junto con su auditoría.
		solicitar(post("/api/planificador/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idArea\":\"" + area + "\",\"codigo\":\"R03\",\"nombre\":\"Sala\",\"ubicacion\":\"Piso 1\"}"))
				.andExpect(status().isInternalServerError());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM room", Integer.class));
		consultas.execute("DROP TRIGGER fallar_auditoria_de_ensayo ON audit_event");
		consultas.execute("""
				CREATE FUNCTION fallo_cambios_de_ensayo() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
				IF NEW.action_audit_event IN ('update','delete') THEN RAISE EXCEPTION 'Fallo controlado'; END IF; RETURN NEW; END; $$;
				CREATE TRIGGER fallo_cambios_de_ensayo BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION fallo_cambios_de_ensayo();
				""");
		solicitar(put(ruta).session(sesion).header("If-Match", revision).contentType(MediaType.APPLICATION_JSON)
				.content(datos(otraArea, profesional, otroConsultorio, "2032-12-02", "08:00", "10:00")))
				.andExpect(status().isInternalServerError());
		solicitar(delete(ruta).session(sesion).header("If-Match", revision)).andExpect(status().isInternalServerError());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
		assertEquals(UUID.fromString(campo(creado, "idPlan")), consultas.queryForObject("SELECT id_schedule FROM shift WHERE id_shift=?",
				UUID.class, UUID.fromString(campo(creado, "id"))));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
	}

	@Test
	void dosAsignacionesConcurrentesSuperpuestasConfirmanUnaSola() throws Exception {
		CountDownLatch inicioConcurrente = new CountDownLatch(1);
		var ejecutor = Executors.newFixedThreadPool(2);
		String datos = datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00");
		try {
			var primera = ejecutor.submit(() -> asignarConcurrente(datos, inicioConcurrente));
			var segunda = ejecutor.submit(() -> asignarConcurrente(datos, inicioConcurrente));
			inicioConcurrente.countDown();
			assertEquals(List.of(201, 409), List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
			assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
			assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
		} finally {
			ejecutor.shutdownNow();
			assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	@Test
	void migracionAusenteYFormulariosInvalidosNoEscriben() throws Exception {
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-13")).andExpect(status().isBadRequest());
		solicitar(get("/api/planificador").session(sesion)).andExpect(status().isBadRequest());
		String datos = datos(area, profesional, consultorio, "2032-11-02", "08:00", "10:00");
		for (String contenido : List.of("{}", "null", "[]", datos.replace("}", ",\"idInstitucion\":\"inventado\"}"))) {
			solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
					.andExpect(status().isBadRequest());
		}
		consultas.execute("ALTER TABLE room RENAME COLUMN id_planning_unit TO columna_no_instalada");
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11")).andExpect(status().isServiceUnavailable());
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
	}

	private int asignarConcurrente(String datos, CountDownLatch inicioConcurrente) throws Exception {
		assertTrue(inicioConcurrente.await(10, TimeUnit.SECONDS));
		return solicitar(post("/api/planificador/turnos").session(sesionDe(institucion, actor)).contentType(MediaType.APPLICATION_JSON)
				.content(datos)).andReturn().getResponse().getStatus();
	}

	private MvcResult crear(String datos) throws Exception {
		return solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos))
				.andExpect(status().isCreated()).andReturn();
	}

	private void rechazar(String datos, int estado, String campo) throws Exception {
		solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos))
				.andExpect(status().is(estado)).andExpect(jsonPath("$.errores." + campo).exists());
	}

	private String campo(MvcResult respuesta, String campo) throws Exception {
		return JsonPath.read(respuesta.getResponse().getContentAsString(), "$." + campo);
	}

	private String datos(UUID area, UUID profesional, UUID consultorio, String fecha, String inicio, String fin) {
		return """
				{"idArea":"%s","idProfesional":"%s","idConsultorio":"%s","idEspecialidad":"%s",
				"fecha":"%s","horaInicio":"%s","horaFin":"%s","observacion":"Ensayo aislado"}
				""".formatted(area, profesional, consultorio, especialidad, fecha, inicio, fin);
	}
}
