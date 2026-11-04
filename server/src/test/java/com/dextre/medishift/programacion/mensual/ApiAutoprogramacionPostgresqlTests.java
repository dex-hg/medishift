package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiAutoprogramacionPostgresqlTests extends SoporteApiPlanesMensuales {
	private UUID area;
	private UUID otraPersona;
	private UUID cuentaDoctor;
	private UUID otraCuenta;
	private MockHttpSession doctor;
	private MockHttpSession otroDoctor;

	@BeforeEach
	void prepararDoctores() throws Exception {
		inicializar(institucion, actor);
		area = nuevaUnidad(institucion, "AUTO");
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidad, area);
		consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", area, consultorio);
		cuentaDoctor = nuevaCuenta(institucion);
		otraCuenta = nuevaCuenta(institucion);
		consultas.update("INSERT INTO role VALUES (100,'doctor','Doctor de ensayo')");
		consultas.update("INSERT INTO user_role VALUES (?,100),(?,100)", cuentaDoctor, otraCuenta);
		consultas.update("UPDATE professional SET id_user_account=? WHERE id_professional=?", cuentaDoctor, profesional);
		otraPersona = nuevoProfesional(otraCuenta, "P02");
		doctor = sesionDe(institucion, cuentaDoctor);
		otroDoctor = sesionDe(institucion, otraCuenta);
	}

	@Test
	void opcionDesactivadaPermiteConsultarPeroSoloAdminPrograma() throws Exception {
		calendario(doctor).andExpect(status().isOk()).andExpect(jsonPath("$.permisos.puedeProgramar").value(false))
				.andExpect(jsonPath("$.permisos.idProfesional").value(profesional.toString()));
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isForbidden());
		crear(sesion, profesional, "08:00", "10:00").andExpect(status().isCreated());
		solicitar(get("/api/sesion").session(doctor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.esDoctor").value(true)).andExpect(jsonPath("$.idProfesional").value(profesional.toString()));
	}

	@Test
	void administraLaOpcionConRevisionYAfectaLaSesionDelDoctorInmediatamente() throws Exception {
		activar(true);
		MvcResult creado = crear(doctor, profesional, "08:00", "10:00").andExpect(status().isCreated()).andReturn();
		activar(false);
		solicitar(put(ruta(creado)).session(doctor).header("If-Match", creado.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional, "09:00", "11:00"))).andExpect(status().isForbidden());
		solicitar(delete(ruta(creado)).session(doctor).header("If-Match", creado.getResponse().getHeader("ETag"))).andExpect(status().isForbidden());
		calendario(doctor).andExpect(jsonPath("$.turnos.length()").value(1));
		solicitar(put(ruta(creado)).session(sesion).header("If-Match", creado.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional, "09:00", "11:00"))).andExpect(status().isOk());
	}

	@Test
	void doctorNoPuedeCrearModificarNiEliminarTurnosDeOtro() throws Exception {
		activar(true);
		crear(doctor, otraPersona, "08:00", "10:00").andExpect(status().isForbidden());
		MvcResult ajeno = crear(otroDoctor, otraPersona, "08:00", "10:00").andExpect(status().isCreated()).andReturn();
		solicitar(put(ruta(ajeno)).session(doctor).header("If-Match", ajeno.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional, "10:00", "11:00"))).andExpect(status().isForbidden());
		solicitar(delete(ruta(ajeno)).session(doctor).header("If-Match", ajeno.getResponse().getHeader("ETag"))).andExpect(status().isForbidden());
	}

	@Test
	void consultaTurnosPropiosYOcupacionAjenaSinNotasNiIdentidadAjena() throws Exception {
		activar(true);
		crear(otroDoctor, otraPersona, "08:00", "10:00").andExpect(status().isCreated());
		calendario(doctor).andExpect(status().isOk()).andExpect(jsonPath("$.turnos.length()").value(0))
				.andExpect(jsonPath("$.profesionales.length()").value(1)).andExpect(jsonPath("$.ocupaciones.length()").value(2))
				.andExpect(jsonPath("$.ocupaciones[0].profesional").doesNotExist())
				.andExpect(jsonPath("$.ocupaciones[0].observacion").doesNotExist());
		calendario(sesion).andExpect(jsonPath("$.turnos.length()").value(1)).andExpect(jsonPath("$.profesionales.length()").value(2));
	}

	@Test
	void impideCrucesDeConsultorioYPermiteIntervalosConsecutivos() throws Exception {
		activar(true);
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isCreated());
		for (String[] horas : List.of(new String[]{"08:00","10:00"}, new String[]{"09:00","11:00"},
				new String[]{"07:00","09:00"}, new String[]{"08:30","09:00"}, new String[]{"07:00","11:00"})) {
			crear(otroDoctor, otraPersona, horas[0], horas[1]).andExpect(status().isConflict())
					.andExpect(jsonPath("$.errores.idConsultorio").exists());
		}
		crear(otroDoctor, otraPersona, "10:00", "12:00").andExpect(status().isCreated());
	}

	@Test
	void dosDoctoresReservandoAlMismoTiempoConfirmanUnSoloTurno() throws Exception {
		activar(true);
		CountDownLatch salida = new CountDownLatch(1);
		try (var ejecutor = Executors.newFixedThreadPool(2)) {
			var primera = ejecutor.submit(() -> reservarConcurrente(cuentaDoctor, profesional, salida));
			var segunda = ejecutor.submit(() -> reservarConcurrente(otraCuenta, otraPersona, salida));
			salida.countDown();
			assertEquals(List.of(201,409), List.of(primera.get(20,TimeUnit.SECONDS), segunda.get(20,TimeUnit.SECONDS)).stream().sorted().toList());
		}
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift WHERE start_at_shift::date='2032-11-02'", Integer.class));
	}

	@Test
	void edicionNoPuedeInvadirReservaAjenaNiUsarRevisionAntigua() throws Exception {
		activar(true);
		MvcResult propio = crear(doctor, profesional, "08:00", "10:00").andExpect(status().isCreated()).andReturn();
		crear(otroDoctor, otraPersona, "10:00", "12:00").andExpect(status().isCreated());
		solicitar(put(ruta(propio)).session(doctor).header("If-Match", propio.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional, "09:00", "11:00"))).andExpect(status().isConflict());
		solicitar(put(ruta(propio)).session(sesion).header("If-Match", propio.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional, "08:30", "10:00"))).andExpect(status().isOk());
		solicitar(delete(ruta(propio)).session(doctor).header("If-Match", propio.getResponse().getHeader("ETag"))).andExpect(status().isConflict());
	}

	@Test
	void respetaEspecialidadDelAreaYEstadoDelConsultorio() throws Exception {
		activar(true);
		UUID especialidadAjena = UUID.randomUUID();
		consultas.update("INSERT INTO specialty(id_specialty,code_specialty,name_specialty) VALUES (?,'OTRA','Otra')", especialidadAjena);
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidadAjena, area);
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isConflict());
		consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidad, area);
		consultas.update("UPDATE room SET status_room='inactive' WHERE id_room=?", consultorio);
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isConflict());
	}

	@Test
	void respetaPermisosAprobadosBloqueosYMaximosDeHoras() throws Exception {
		activar(true);
		consultas.update("""
				INSERT INTO professional_leave VALUES (?,?,'2032-11-02 08:00-05','2032-11-02 10:00-05','Ensayo','approved')
				""", UUID.randomUUID(), profesional);
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isConflict());
		consultas.update("DELETE FROM professional_leave WHERE id_professional=?", profesional);
		crear(doctor, profesional, "08:00", "14:01").andExpect(status().isConflict());
		consultas.update("""
				INSERT INTO room_block VALUES (?,?,'2032-11-02 08:00-05','2032-11-02 10:00-05','Ensayo','active')
				""", UUID.randomUUID(), consultorio);
		crear(doctor, profesional, "08:00", "10:00").andExpect(status().isConflict());
	}

	@Test
	void protegeCatalogosHistorialSemanalConfiguracionYAsistenciaDelPersonal() throws Exception {
		activar(true);
		for (String ruta : List.of("/api/profesionales","/api/consultorios","/api/especialidades","/api/horarios/recursos")) {
			solicitar(get(ruta).session(doctor)).andExpect(status().isForbidden());
		}
		solicitar(post("/api/planificador/areas").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
		solicitar(post("/api/horarios").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
		solicitar(put("/api/configuracion").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
		MvcResult propio = crear(doctor, profesional, "08:00", "10:00").andExpect(status().isCreated()).andReturn();
		solicitar(put(ruta(propio) + "/asistencia").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
	}

	@Test
	void revocacionDelRolOInactivacionProfesionalImpidenAccesoConSesionAbierta() throws Exception {
		activar(true);
		consultas.update("DELETE FROM user_role WHERE id_user_account=? AND id_role=100", cuentaDoctor);
		calendario(doctor).andExpect(status().isForbidden());
		consultas.update("INSERT INTO user_role VALUES (?,100)", cuentaDoctor);
		consultas.update("UPDATE professional SET status_professional='inactive' WHERE id_professional=?", profesional);
		calendario(doctor).andExpect(status().isForbidden());
	}

	@Test
	void administradorHabilitaCuentaSinExponerClavesYPuedeRevocarSoloElRolDoctor() throws Exception {
		UUID nuevo = nuevoProfesional(null, "P03");
		String rutaAcceso = "/api/profesionales/" + nuevo + "/acceso";
		MvcResult acceso = solicitar(get(rutaAcceso).session(sesion)).andExpect(status().isOk()).andReturn();
		String contenido = "{\"correo\":\"nuevo@medishift.test\",\"contrasena\":\"Clave ficticia de ensayo\",\"habilitado\":true}";
		MvcResult habilitado = solicitar(put(rutaAcceso).session(sesion).header("If-Match", acceso.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitado").value(true)).andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
		String cuenta = JsonPath.read(habilitado.getResponse().getContentAsString(), "$.idCuenta");
		assertNotEquals("Clave ficticia de ensayo", consultas.queryForObject("SELECT password_hash_user_account FROM user_account WHERE id_user_account=?",
				String.class, UUID.fromString(cuenta)));
		solicitar(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content("""
				{"codigoInstitucion":"%s","correo":"nuevo@medishift.test","contrasena":"Clave ficticia de ensayo"}
				""".formatted(consultas.queryForObject("SELECT code_institution FROM institution WHERE id_institution=?", String.class, institucion))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.esDoctor").value(true));
		solicitar(put(rutaAcceso).session(sesion).header("If-Match", habilitado.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"nuevo@medishift.test\",\"contrasena\":\"\",\"habilitado\":false}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.habilitado").value(false));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE after_audit_event::text LIKE '%Clave ficticia%'", Integer.class));
	}

	@Test
	void accesoRechazaSuplantacionRevisionViejaYCredencialesInvalidas() throws Exception {
		String acceso = "/api/profesionales/" + profesional + "/acceso";
		solicitar(get(acceso).session(doctor)).andExpect(status().isForbidden());
		UUID ajena = nuevoProfesional(null, "P03");
		String rutaAcceso = "/api/profesionales/" + ajena + "/acceso";
		MvcResult actual = solicitar(get(rutaAcceso).session(sesion)).andExpect(status().isOk()).andReturn();
		solicitar(put(rutaAcceso).session(sesion).header("If-Match", actual.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"incorrecto\",\"contrasena\":\"corta\",\"habilitado\":true}"))
				.andExpect(status().isBadRequest());
		solicitar(put(rutaAcceso).session(sesion).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isPreconditionRequired());
		solicitar(get("/api/planificador").param("periodo","2032-11")).andExpect(status().isUnauthorized());
		activar(true);
		solicitar(post("/api/planificador/turnos").session(doctor).header("Origin","https://ajeno.invalid")
				.contentType(MediaType.APPLICATION_JSON).content(datos(profesional,"08:00","10:00"))).andExpect(status().isForbidden());
	}

	private UUID nuevoProfesional(UUID cuenta, String codigo) {
		UUID persona = UUID.randomUUID();
		consultas.update("""
				INSERT INTO professional(id_professional,id_institution,id_user_account,first_name_professional,last_name_professional,
				license_number_professional,category_professional,email_professional,status_professional)
				VALUES (?,?,?,'Otro','Doctor',?,'Medico',?,'active')
				""", persona, institucion, cuenta, codigo, codigo + "@medishift.test");
		consultas.update("INSERT INTO professional_specialty VALUES (?,?,true)", persona, especialidad);
		return persona;
	}

	private void activar(boolean habilitada) throws Exception {
		MvcResult actual = solicitar(get("/api/configuracion").session(sesion)).andExpect(status().isOk()).andReturn();
		solicitar(put("/api/configuracion").session(sesion).header("If-Match", actual.getResponse().getHeader("ETag"))
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"nombreInstitucion":"Ensayo aislado","zonaHoraria":"America/Lima","minutosDiarios":360,
				"minutosSemanales":2160,"minutosPorFeriado":360,"autoprogramacionMedica":%s}
				""".formatted(habilitada))).andExpect(status().isOk()).andExpect(jsonPath("$.autoprogramacionMedica").value(habilitada));
	}

	private ResultActions calendario(MockHttpSession cuenta) throws Exception {
		return solicitar(get("/api/planificador").session(cuenta).param("periodo","2032-11"));
	}

	private ResultActions crear(MockHttpSession cuenta, UUID persona, String inicio, String fin) throws Exception {
		return solicitar(post("/api/planificador/turnos").session(cuenta).contentType(MediaType.APPLICATION_JSON).content(datos(persona,inicio,fin)));
	}

	private int reservarConcurrente(UUID cuenta, UUID persona, CountDownLatch salida) throws Exception {
		assertTrue(salida.await(10,TimeUnit.SECONDS));
		return crear(sesionDe(institucion,cuenta),persona,"08:00","10:00").andReturn().getResponse().getStatus();
	}

	private String ruta(MvcResult resultado) throws Exception {
		return "/api/planificador/turnos/" + JsonPath.<String>read(resultado.getResponse().getContentAsString(),"$.id");
	}

	private String datos(UUID persona, String inicio, String fin) {
		return """
				{"idArea":"%s","idProfesional":"%s","idConsultorio":"%s","idEspecialidad":"%s",
				"fecha":"2032-11-02","horaInicio":"%s","horaFin":"%s","observacion":"Ensayo privado"}
				""".formatted(area,persona,consultorio,especialidad,inicio,fin);
	}
}
