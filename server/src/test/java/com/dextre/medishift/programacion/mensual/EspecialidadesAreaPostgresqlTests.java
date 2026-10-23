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

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contratos y transacciones reales en un esquema privado que se elimina al terminar. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class EspecialidadesAreaPostgresqlTests extends SoporteApiPlanesMensuales {

	private UUID dermatologia;
	private UUID odontologia;
	private UUID areaOdontologia;
	private UUID salaOdontologia;

	@BeforeEach
	void prepararEspecialidadesYArea() throws Exception {
		inicializar(institucion, actor);
		dermatologia = crearEspecialidad("Dermatología", sesion);
		odontologia = crearEspecialidad("Odontología", sesion);
		consultas.update("UPDATE professional_specialty SET is_primary_professional_specialty=false WHERE id_professional=?", profesional);
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,true)", profesional, dermatologia);
		areaOdontologia = campoId(solicitar(post("/api/planificador/areas").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(area("ODO", "Atención dental", odontologia))).andExpect(status().isCreated()).andReturn());
		salaOdontologia = campoId(solicitar(post("/api/planificador/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idArea\":\"" + areaOdontologia + "\",\"codigo\":\"ODO-01\",\"nombre\":\"Sala dental\",\"ubicacion\":\"Piso 1\"}"))
				.andExpect(status().isCreated()).andReturn());
	}

	@Test
	void bloqueaDermatologoEnOdontologiaAunqueElConsultorioFisicoSeaGeneral() throws Exception {
		assertTrue(consultas.queryForObject("SELECT is_general_room FROM room WHERE id_room=?", Boolean.class, salaOdontologia));
		for (UUID especialidadEnviada : List.of(dermatologia, odontologia)) {
			solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON)
					.content(turno(profesional, especialidadEnviada))).andExpect(status().isConflict())
					.andExpect(jsonPath("$.errores.idEspecialidad").exists());
		}
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule='monthly'", Integer.class));
	}

	@Test
	void odontologoCompatibleSeGuardaYLosRecursosSoloOfrecenLaEspecialidadAutorizada() throws Exception {
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,false)", profesional, odontologia);
		solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(turno(profesional, odontologia)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.idEspecialidad").value(odontologia.toString()));
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2032-11"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.consultorios[?(@.id == '" + salaOdontologia + "')].usoGeneral").value(hasItem(false)))
				.andExpect(jsonPath("$.consultorios[?(@.id == '" + salaOdontologia + "')].especialidades[0].id").value(hasItem(odontologia.toString())));
	}

	@Test
	void elFlujoSemanalTampocoPuedeEludirLaEspecialidadDelArea() throws Exception {
		String semanal = turno(profesional, dermatologia).replace("\"idArea\":\"" + areaOdontologia + "\",", "")
				.replace("2032-11-02", "2032-11-08");
		solicitar(post("/api/horarios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(semanal))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idConsultorio").exists());
	}

	@Test
	void nuevasEspecialidadesSonPrivadasYLosNombresIgualesNoConcedenAccesoEntreInstituciones() throws Exception {
		UUID otraInstitucion = nuevaInstitucion();
		UUID otraCuenta = nuevoActor(otraInstitucion);
		MockHttpSession otraSesion = sesionDe(otraInstitucion, otraCuenta);
		solicitar(get("/api/especialidades").session(otraSesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(not(hasItem(dermatologia.toString()))));
		UUID independiente = crearEspecialidad("Dermatología", otraSesion);
		assertNotEquals(dermatologia, independiente);
		solicitar(put("/api/planificador/areas/" + areaOdontologia + "/especialidad").session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content("{\"idEspecialidad\":\"" + independiente + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idEspecialidad").exists());
	}

	@Test
	void rechazaDuplicadosNormalizadosYFormulariosInvalidosSinEscribir() throws Exception {
		int anteriores = consultas.queryForObject("SELECT count(*) FROM specialty", Integer.class);
		solicitar(post("/api/especialidades").session(sesion).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  DERMATOLOGÍA  \"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.nombre").exists());
		for (String contenido : List.of("{}", "{\"nombre\":null}", "{\"nombre\":123}", "{\"nombre\":\" \"}",
				"{\"nombre\":\"Neurología\",\"idInstitucion\":\"ajena\"}", "{\"nombre\":\"" + "a".repeat(101) + "\"}")) {
			solicitar(post("/api/especialidades").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
					.andExpect(status().isBadRequest());
		}
		assertEquals(anteriores, consultas.queryForObject("SELECT count(*) FROM specialty", Integer.class));
	}

	@Test
	void altasYConfiguracionExigenSesionVigenteYElAreaExigeAdministrador() throws Exception {
		solicitar(post("/api/especialidades").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Neurología\"}"))
				.andExpect(status().isUnauthorized());
		UUID sinPermiso = nuevaCuenta(institucion);
		solicitar(post("/api/planificador/areas").session(sesionDe(institucion, sinPermiso)).contentType(MediaType.APPLICATION_JSON)
				.content(area("DER", "Área nueva", dermatologia))).andExpect(status().isForbidden());
		consultas.update("UPDATE user_account SET status_user_account='inactive' WHERE id_user_account=?", actor);
		solicitar(get("/api/especialidades").session(sesion)).andExpect(status().isUnauthorized());
	}

	@Test
	void areaAnteriorDebeConfigurarseYNoSeInventaSuEspecialidad() throws Exception {
		UUID anterior = nuevaUnidad(institucion, "ANT");
		consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", anterior, consultorio);
		String datos = turno(profesional, dermatologia).replace(areaOdontologia.toString(), anterior.toString())
				.replace(salaOdontologia.toString(), consultorio.toString());
		solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idArea").exists());
		// Un turno histórico no determina la especialidad ni se modifica al configurar el área.
		consultas.update("UPDATE shift SET start_at_shift='2020-01-01 08:00-05',end_at_shift='2020-01-01 10:00-05'");
		solicitar(put("/api/planificador/areas/" + anterior + "/especialidad").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idEspecialidad\":\"" + dermatologia + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.idEspecialidad").value(dermatologia.toString()));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift WHERE extract(year FROM start_at_shift)=2020", Integer.class));
	}

	@Test
	void cambiarEspecialidadNoInvalidaTurnosFuturosNiBorraSusDatos() throws Exception {
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,false)", profesional, odontologia);
		solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(turno(profesional, odontologia)))
				.andExpect(status().isCreated());
		solicitar(put("/api/planificador/areas/" + areaOdontologia + "/especialidad").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idEspecialidad\":\"" + dermatologia + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idEspecialidad").exists());
		assertEquals(odontologia, consultas.queryForObject("SELECT id_specialty FROM planning_unit WHERE id_planning_unit=?", UUID.class, areaOdontologia));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
	}

	@Test
	void unaSalaRestringidaNoPuedeVincularseAUnAreaIncompatible() throws Exception {
		consultas.update("UPDATE room SET is_general_room=false WHERE id_room=?", consultorio);
		consultas.update("INSERT INTO room_specialty VALUES (?,?)", consultorio, dermatologia);
		solicitar(put("/api/planificador/consultorios/" + consultorio + "/area").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idArea\":\"" + areaOdontologia + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idConsultorio").exists());
		assertNull(consultas.queryForObject("SELECT id_planning_unit FROM room WHERE id_room=?", UUID.class, consultorio));
	}

	@Test
	void vincularUnaSalaGeneralNoInvalidaSusTurnosSemanalesFuturos() throws Exception {
		solicitar(put("/api/planificador/consultorios/" + consultorio + "/area").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idArea\":\"" + areaOdontologia + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idConsultorio").exists());
		assertNull(consultas.queryForObject("SELECT id_planning_unit FROM room WHERE id_room=?", UUID.class, consultorio));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift", Integer.class));
	}

	@Test
	void falloDeAuditoriaRevierteLaConfiguracionDelArea() throws Exception {
		consultas.execute("""
				CREATE FUNCTION fallo_auditoria_especialidad() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
				IF NEW.entity_name_audit_event='planning_unit' AND NEW.action_audit_event='update'
				THEN RAISE EXCEPTION 'Fallo de ensayo'; END IF; RETURN NEW; END $$;
				CREATE TRIGGER fallo_especialidad BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION fallo_auditoria_especialidad();
				""");
		solicitar(put("/api/planificador/areas/" + areaOdontologia + "/especialidad").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idEspecialidad\":\"" + dermatologia + "\"}"))
				.andExpect(status().isInternalServerError());
		assertEquals(odontologia, consultas.queryForObject("SELECT id_specialty FROM planning_unit WHERE id_planning_unit=?", UUID.class, areaOdontologia));
	}

	@Test
	void dosAltasConcurrentesDelMismoNombreCreanUnaSolaEspecialidad() throws Exception {
		CountDownLatch inicioConcurrente = new CountDownLatch(1);
		var ejecutor = Executors.newFixedThreadPool(2);
		try {
			var tarea = (java.util.concurrent.Callable<Integer>) () -> {
				assertTrue(inicioConcurrente.await(10, TimeUnit.SECONDS));
				return solicitar(post("/api/especialidades").session(sesionDe(institucion, actor)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Cardiología concurrente\"}")).andReturn().getResponse().getStatus();
			};
			var primera = ejecutor.submit(tarea);
			var segunda = ejecutor.submit(tarea);
			inicioConcurrente.countDown();
			assertEquals(List.of(201, 409), List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
			assertEquals(1, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty='Cardiología concurrente'", Integer.class));
		} finally { ejecutor.shutdownNow(); assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS)); }
	}

	private UUID crearEspecialidad(String nombre, MockHttpSession sesionActual) throws Exception {
		return campoId(solicitar(post("/api/especialidades").session(sesionActual).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\"}")).andExpect(status().isCreated()).andReturn());
	}

	private UUID campoId(MvcResult resultado) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
	}

	private String area(String codigo, String nombre, UUID especialidadArea) {
		return "{\"codigo\":\"" + codigo + "\",\"nombre\":\"" + nombre + "\",\"idEspecialidad\":\"" + especialidadArea + "\"}";
	}

	private String turno(UUID persona, UUID especialidadTurno) {
		return """
				{"idArea":"%s","idProfesional":"%s","idConsultorio":"%s","idEspecialidad":"%s",
				"fecha":"2032-11-02","horaInicio":"08:00","horaFin":"10:00","observacion":""}
				""".formatted(areaOdontologia, persona, salaOdontologia, especialidadTurno);
	}
}
