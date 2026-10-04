package com.dextre.medishift.programacion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixtures identificados por UUID; nunca modifica datos ajenos ni el esquema. */
abstract class SoporteProgramacionPostgresql {

	@Autowired protected JdbcTemplate consultas;
	@Autowired private WebApplicationContext contexto;
	@Autowired private PlatformTransactionManager gestor;
	protected MockMvc cliente;
	protected MockHttpSession sesion;
	protected CuentaTemporal cuenta;
	protected UUID profesional;
	protected UUID consultorio;
	protected UUID especialidad;
	protected LocalDate lunes;
	private final List<CuentaTemporal> cuentas = new ArrayList<>();
	private final List<UUID> especialidades = new ArrayList<>();

	@BeforeEach
	void prepararRecursosRealesTemporales() {
		cliente = MockMvcBuilders.webAppContextSetup(contexto)
				.addFilters(contexto.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
		cuenta = nuevaCuenta();
		sesion = sesionDe(cuenta);
		lunes = TiempoProgramacion.inicioSemana(LocalDate.now(ZoneId.of("America/Lima")).plusWeeks(3));
		especialidad = nuevaEspecialidad();
		profesional = nuevoProfesional(cuenta, especialidad);
		consultorio = nuevoConsultorio(cuenta, true, especialidad);
		for (int dia = 1; dia <= 7; dia++) { disponibilidad(profesional, dia, "00:00", "23:59"); }
	}

	@AfterEach
	void limpiarSoloInstitucionesTemporales() {
		new TransactionTemplate(gestor).executeWithoutResult(estado -> {
			for (CuentaTemporal temporal : cuentas) {
				UUID institucion = temporal.institucion();
				consultas.update("DELETE FROM attendance WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM notification WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM shift WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM coverage_requirement WHERE id_schedule IN "
						+ "(SELECT id_schedule FROM schedule WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM generation_run WHERE id_schedule IN "
						+ "(SELECT id_schedule FROM schedule WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM schedule WHERE id_institution = ?", institucion);
				for (String tabla : List.of("availability", "professional_leave", "professional_specialty")) {
					consultas.update("DELETE FROM " + tabla + " WHERE id_professional IN "
							+ "(SELECT id_professional FROM professional WHERE id_institution = ?)", institucion);
				}
				consultas.update("DELETE FROM professional WHERE id_institution = ?", institucion);
				for (String tabla : List.of("room_block", "room_specialty")) {
					consultas.update("DELETE FROM " + tabla + " WHERE id_room IN "
							+ "(SELECT id_room FROM room WHERE id_institution = ?)", institucion);
				}
				consultas.update("DELETE FROM room WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM user_account WHERE id_user_account = ? AND id_institution = ?",
						temporal.usuario(), institucion);
				consultas.update("DELETE FROM institution WHERE id_institution = ?", institucion);
			}
			for (UUID id : especialidades) { consultas.update("DELETE FROM specialty WHERE id_specialty = ?", id); }
		});
	}

	protected CuentaTemporal nuevaCuenta() {
		CuentaTemporal resultado = new CuentaTemporal(UUID.randomUUID(), UUID.randomUUID());
		cuentas.add(resultado);
		consultas.update("INSERT INTO institution VALUES (?, ?, ?, 'America/Lima', 'active')",
				resultado.institucion(), "T-hor-" + resultado.institucion().toString().substring(0, 24), "Prueba horarios");
		consultas.update("""
				INSERT INTO user_account (id_user_account,id_institution,email_user_account,
				password_hash_user_account,status_user_account) VALUES (?,?,'horarios@medishift.test','hash-fixture','active')
				""", resultado.usuario(), resultado.institucion());
		return resultado;
	}

	protected MockHttpSession sesionDe(CuentaTemporal temporal) {
		MockHttpSession resultado = new MockHttpSession();
		resultado.setAttribute("medishift.idCuenta", temporal.usuario());
		resultado.setAttribute("medishift.idInstitucion", temporal.institucion());
		return resultado;
	}

	protected UUID nuevaEspecialidad() {
		UUID id = UUID.randomUUID();
		especialidades.add(id);
		consultas.update("INSERT INTO specialty VALUES (?, ?, ?)", id,
				"T-hor-" + id.toString().substring(0, 24), "Especialidad temporal " + id);
		return id;
	}

	protected UUID nuevoProfesional(CuentaTemporal temporal, UUID competencia) {
		UUID id = UUID.randomUUID();
		consultas.update("""
				INSERT INTO professional (id_professional,id_institution,first_name_professional,
				last_name_professional,license_number_professional,category_professional,email_professional,status_professional)
				VALUES (?,?,'Temporal','Horarios',?,'Médico',?,'active')
				""", id, temporal.institucion(), id.toString().substring(0, 24), id + "@medishift.test");
		consultas.update("INSERT INTO professional_specialty VALUES (?, ?, true)", id, competencia);
		return id;
	}

	protected UUID nuevoConsultorio(CuentaTemporal temporal, boolean general, UUID competencia) {
		UUID id = UUID.randomUUID();
		consultas.update("INSERT INTO room VALUES (?, ?, ?, 'Sala temporal', 'Piso 1', ?, 'active')",
				id, temporal.institucion(), "R-" + id.toString().substring(0, 12), general);
		if (!general) { consultas.update("INSERT INTO room_specialty VALUES (?, ?)", id, competencia); }
		return id;
	}

	protected UUID disponibilidad(UUID idProfesional, int dia, String inicio, String fin) {
		UUID id = UUID.randomUUID();
		consultas.update("INSERT INTO availability VALUES (?, ?, ?, ?::time, ?::time, ?::date, ?::date, 'active', '')",
				id, idProfesional, dia, inicio, fin, lunes.minusWeeks(1).toString(), lunes.plusWeeks(2).toString());
		return id;
	}

	protected String turno(LocalDate fecha, String inicio, String fin) {
		return turno(profesional, consultorio, especialidad, fecha, inicio, fin);
	}

	protected String turno(UUID persona, UUID sala, UUID competencia, LocalDate fecha, String inicio, String fin) {
		return """
				{"idProfesional":"%s","idConsultorio":"%s","idEspecialidad":"%s",
				"fecha":"%s","horaInicio":"%s","horaFin":"%s","observacion":"Prueba temporal"}
				""".formatted(persona, sala, competencia, fecha, inicio, fin);
	}

	protected String crearTurno(LocalDate fecha, String inicio, String fin) throws Exception {
		return cliente.perform(json(post("/api/horarios"), turno(fecha, inicio, fin)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}

	protected String semana() throws Exception {
		return cliente.perform(get("/api/horarios").param("fechaInicio", lunes.toString()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	protected String accion(String ruta, String vista) {
		return """
				{"fechaInicio":"%s","idHorario":"%s","revision":"%s"}
				""".formatted(lunes, JsonPath.read(vista, "$.idHorario"), JsonPath.read(vista, "$.revision"));
	}

	protected String aprobar() throws Exception {
		return cliente.perform(json(post("/api/horarios/aprobar"), accion("aprobar", semana())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	protected String revision(String vista) { return "\"" + JsonPath.read(vista, "$.revision") + "\""; }
	protected UUID id(String vista) { return UUID.fromString(JsonPath.read(vista, "$.id")); }
	protected UUID idHorario(String vista) { return UUID.fromString(JsonPath.read(vista, "$.idHorario")); }

	protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder solicitud, String contenido) {
		return solicitud.session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido);
	}

	protected Instant instante(LocalDate fecha, String hora) {
		return TiempoProgramacion.resolver(fecha, LocalTime.parse(hora), ZoneId.of("America/Lima"));
	}

	protected UUID horarioLegado(LocalDate desde, LocalDate hasta) {
		UUID id = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule,id_institution,period_start_schedule,period_end_schedule,
				version_schedule,status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule)
				VALUES (?,?,?::date,?::date,1,'approved',?,?,now())
				""", id, cuenta.institucion(), desde.toString(), hasta.toString(), cuenta.usuario(), cuenta.usuario());
		return id;
	}

	protected void turnoLegado(UUID horario, Instant inicio, Instant fin) {
		consultas.update("""
				INSERT INTO shift (id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
				start_at_shift,end_at_shift,status_shift) VALUES (?,?,?,?,?,?,?,?,'approved')
				""", UUID.randomUUID(), cuenta.institucion(), horario, profesional, consultorio, especialidad,
				Timestamp.from(inicio), Timestamp.from(fin));
	}

	protected record CuentaTemporal(UUID institucion, UUID usuario) { }
}
