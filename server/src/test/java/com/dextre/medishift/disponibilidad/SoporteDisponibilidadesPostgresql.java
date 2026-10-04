package com.dextre.medishift.disponibilidad;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class SoporteDisponibilidadesPostgresql {

	@Autowired
	protected JdbcTemplate consultas;
	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private PlatformTransactionManager gestor;
	protected MockMvc cliente;
	protected CuentaPrueba cuenta;
	protected MockHttpSession sesion;
	protected UUID profesional;
	protected LocalDate lunes;
	private final List<CuentaPrueba> cuentas = new ArrayList<>();
	private final List<UUID> profesionales = new ArrayList<>();
	private final List<UUID> disponibilidades = new ArrayList<>();
	private final List<UUID> turnos = new ArrayList<>();
	private final List<UUID> horarios = new ArrayList<>();
	private final List<UUID> consultorios = new ArrayList<>();
	private final List<UUID> especialidades = new ArrayList<>();

	@BeforeEach
	void prepararFixturePropioYClienteReal() {
		cliente = MockMvcBuilders.webAppContextSetup(contexto)
				.addFilters(contexto.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
		cuenta = crearCuenta();
		sesion = sesionDe(cuenta);
		profesional = crearProfesional(cuenta.institucion(), "active");
		lunes = LocalDate.now(ZoneId.of("America/Lima")).plusMonths(2)
				.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
	}

	@AfterEach
	void eliminarExclusivamenteIdentificadoresCreadosPorEstaPrueba() {
		new TransactionTemplate(gestor).executeWithoutResult(estado -> {
			for (UUID id : turnos) { consultas.update("DELETE FROM shift WHERE id_shift = ?", id); }
			for (UUID id : horarios) { consultas.update("DELETE FROM schedule WHERE id_schedule = ?", id); }
			for (UUID id : disponibilidades) { consultas.update("DELETE FROM availability WHERE id_availability = ?", id); }
			for (UUID id : profesionales) {
				consultas.update("DELETE FROM professional_specialty WHERE id_professional = ?", id);
				consultas.update("DELETE FROM professional WHERE id_professional = ?", id);
			}
			for (UUID id : consultorios) { consultas.update("DELETE FROM room WHERE id_room = ?", id); }
			for (UUID id : especialidades) { consultas.update("DELETE FROM specialty WHERE id_specialty = ?", id); }
			for (CuentaPrueba fixture : cuentas) {
				consultas.update("DELETE FROM user_account WHERE id_user_account = ? AND id_institution = ?",
						fixture.cuenta(), fixture.institucion());
				consultas.update("DELETE FROM institution WHERE id_institution = ?", fixture.institucion());
			}
		});
	}

	protected CuentaPrueba crearCuenta() {
		CuentaPrueba fixture = new CuentaPrueba(UUID.randomUUID(), UUID.randomUUID());
		cuentas.add(fixture);
		consultas.update("INSERT INTO institution VALUES (?, ?, 'Institución temporal disponibilidad', 'America/Lima', 'active')",
				fixture.institucion(), "T-disp-" + fixture.institucion().toString().substring(0, 23));
		consultas.update("""
				INSERT INTO user_account (id_user_account, id_institution, email_user_account,
				password_hash_user_account, status_user_account)
				VALUES (?, ?, 'disponibilidad@medishift.test', 'hash-fixture-sin-acceso', 'active')
				""", fixture.cuenta(), fixture.institucion());
		return fixture;
	}

	protected MockHttpSession sesionDe(CuentaPrueba fixture) {
		MockHttpSession resultado = new MockHttpSession();
		resultado.setAttribute("medishift.idCuenta", fixture.cuenta());
		resultado.setAttribute("medishift.idInstitucion", fixture.institucion());
		return resultado;
	}

	protected UUID crearProfesional(UUID institucion, String estado) {
		UUID id = UUID.randomUUID();
		profesionales.add(id);
		consultas.update("""
				INSERT INTO professional (id_professional, id_institution, first_name_professional,
				last_name_professional, license_number_professional, category_professional,
				email_professional, status_professional)
				VALUES (?, ?, 'Ana María', 'Pérez Díaz', ?, 'Médico', ?, ?)
				""", id, institucion, id.toString().substring(0, 32), id + "@medishift.test", estado);
		return id;
	}

	protected UUID crearDisponibilidad(String contenido) throws Exception {
		String respuesta = cliente.perform(json(post("/api/disponibilidades"), contenido))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(respuesta, "$.id"));
		disponibilidades.add(id);
		return id;
	}

	protected String revision(UUID disponibilidad) throws Exception {
		String respuesta = cliente.perform(get("/api/disponibilidades/{id}", disponibilidad).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return '"' + JsonPath.<String>read(respuesta, "$.revision") + '"';
	}

	protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder solicitud, String contenido) {
		return solicitud.session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido);
	}

	protected String disponibilidad(UUID idProfesional, int dia, String inicio, String fin,
			LocalDate fechaInicio, LocalDate fechaFin, String estado) {
		return """
				{"idProfesional":"%s","diaSemana":%d,"horaInicio":"%s","horaFin":"%s",
				 "fechaInicio":"%s","fechaFin":"%s","estado":"%s","observacion":"Ventana de prueba"}
				""".formatted(idProfesional, dia, inicio, fin, fechaInicio, fechaFin, estado);
	}

	protected String ventana(String inicio, String fin, String estado) {
		return disponibilidad(profesional, 1, inicio, fin, lunes, lunes.plusDays(6), estado);
	}

	protected UUID crearTurno(String estado) {
		UUID consultorio = UUID.randomUUID();
		consultorios.add(consultorio);
		consultas.update("""
				INSERT INTO room (id_room, id_institution, code_room, name_room, location_room, is_general_room, status_room)
				VALUES (?, ?, 'T-001', 'Consultorio temporal', 'Piso 1', true, 'active')
				""", consultorio, cuenta.institucion());
		UUID especialidad = UUID.randomUUID();
		especialidades.add(especialidad);
		consultas.update("INSERT INTO specialty VALUES (?, ?, ?)", especialidad,
				"T-disp-" + especialidad.toString().substring(0, 23), "Especialidad temporal " + especialidad);
		consultas.update("INSERT INTO professional_specialty VALUES (?, ?, true)", profesional, especialidad);
		UUID horario = UUID.randomUUID();
		horarios.add(horario);
		consultas.update("""
				INSERT INTO schedule (id_schedule, id_institution, period_start_schedule, period_end_schedule,
				version_schedule, status_schedule, id_creator_user_account)
				VALUES (?, ?, ?, ?, 1, 'draft', ?)
				""", horario, cuenta.institucion(), Date.valueOf(lunes), Date.valueOf(lunes.plusDays(6)), cuenta.cuenta());
		UUID turno = UUID.randomUUID();
		turnos.add(turno);
		consultas.update("""
				INSERT INTO shift (id_shift, id_institution, id_schedule, id_professional, id_room,
				id_specialty, start_at_shift, end_at_shift, status_shift)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", turno, cuenta.institucion(), horario, profesional, consultorio, especialidad,
				Timestamp.from(lunes.atTime(LocalTime.of(8, 0)).atZone(ZoneId.of("America/Lima")).toInstant()),
				Timestamp.from(lunes.atTime(LocalTime.of(14, 0)).atZone(ZoneId.of("America/Lima")).toInstant()), estado);
		return turno;
	}

	protected record CuentaPrueba(UUID institucion, UUID cuenta) {
	}

}
