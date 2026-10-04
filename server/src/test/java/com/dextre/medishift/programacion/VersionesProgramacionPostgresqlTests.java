package com.dextre.medishift.programacion;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-horarios", matches = "true")
class VersionesProgramacionPostgresqlTests extends SoporteProgramacionPostgresql {

	@Test
	void editarBorradorExigeRevisionYRechazaEdicionesPerdidas() throws Exception {
		String creado = crearTurno(lunes, "08:00", "10:00");
		cliente.perform(json(put("/api/horarios/" + id(creado)), turno(lunes, "08:00", "11:00")))
				.andExpect(status().isPreconditionRequired());
		cliente.perform(json(put("/api/horarios/" + id(creado)).header("If-Match", revision(creado)),
				turno(lunes, "08:00", "11:00"))).andExpect(status().isOk());
		cliente.perform(delete("/api/horarios/" + id(creado)).session(sesion).header("If-Match", revision(creado)))
				.andExpect(status().isConflict());
	}

	@Test
	void aprobadosInmutablesYReemplazoConservaVersionAnterior() throws Exception {
		String creado = crearTurno(lunes, "08:00", "14:00");
		String aprobado = aprobar();
		UUID anterior = idHorario(aprobado);
		String turnoAprobado = cliente.perform(get("/api/horarios/" + id(creado)).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		cliente.perform(json(put("/api/horarios/" + id(creado)).header("If-Match", revision(turnoAprobado)),
				turno(lunes, "08:00", "10:00"))).andExpect(status().isConflict());
		cliente.perform(delete("/api/horarios/" + id(creado)).session(sesion).header("If-Match", revision(turnoAprobado)))
				.andExpect(status().isConflict());
		String reabierto = cliente.perform(json(post("/api/horarios/reabrir"), accion("reabrir", aprobado)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.versionAprobada").value(1)).andReturn().getResponse().getContentAsString();
		assertNotEquals(anterior, idHorario(reabierto));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ? AND status_shift = 'approved'",
				Integer.class, cuenta.institucion()));
		String reemplazo = aprobar();
		assertEquals("Aprobado", JsonPath.read(reemplazo, "$.estado"));
		assertEquals("superseded", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?",
				String.class, anterior));
		assertEquals("superseded", consultas.queryForObject("SELECT status_shift FROM shift WHERE id_shift = ?",
				String.class, id(creado)));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ? AND status_shift = 'approved'",
				Integer.class, cuenta.institucion()));
	}

	@Test
	void cancelacionAnulaBorradorYAprobacionVigenteSinBorrarHistorial() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		String aprobado = aprobar();
		cliente.perform(json(post("/api/horarios/reabrir"), accion("reabrir", aprobado))).andExpect(status().isOk());
		cliente.perform(json(post("/api/horarios/cancelar"), accion("cancelar", semana())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Cancelado"));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM schedule WHERE id_institution = ? AND status_schedule = 'cancelled'",
				Integer.class, cuenta.institucion()));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ? AND status_shift = 'cancelled'",
				Integer.class, cuenta.institucion()));
		String nuevo = crearTurno(lunes, "08:00", "10:00");
		assertNotNull(id(nuevo));
		assertEquals(3, JsonPath.<Integer>read(semana(), "$.version"));
	}

	@Test
	void semanaAntiguaNoPuedeAprobarCambiosQueUsuarioNoVio() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		String antigua = semana();
		crearTurno(lunes, "10:00", "12:00");
		cliente.perform(json(post("/api/horarios/aprobar"), accion("aprobar", antigua)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.revision").exists());
	}

	@Test
	void coberturaSeConservaAlReabrirYFalloNoSustituyeAprobacionAnterior() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		UUID horario = idHorario(semana());
		consultas.update("INSERT INTO coverage_requirement VALUES (?, ?, ?, ?, ?, 1, 'Temporal')", UUID.randomUUID(),
				horario, especialidad, Timestamp.from(instante(lunes, "08:00")), Timestamp.from(instante(lunes, "10:00")));
		String aprobado = aprobar();
		String copia = cliente.perform(json(post("/api/horarios/reabrir"), accion("reabrir", aprobado)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM coverage_requirement WHERE id_schedule = ?",
				Integer.class, idHorario(copia)));
		String identificador = JsonPath.read(copia, "$.turnos[0].id");
		String revision = "\"" + JsonPath.read(copia, "$.turnos[0].revision") + "\"";
		cliente.perform(json(put("/api/horarios/" + identificador).header("If-Match", revision),
				turno(lunes, "08:00", "09:00"))).andExpect(status().isOk());
		cliente.perform(json(post("/api/horarios/aprobar"), accion("aprobar", semana()))).andExpect(status().isConflict());
		assertEquals("approved", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?",
				String.class, horario));
		assertEquals("draft", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?",
				String.class, idHorario(copia)));
	}

	@Test
	void autenticaAislaInstitucionesYNoCacheaRespuestas() throws Exception {
		String creado = crearTurno(lunes, "08:00", "10:00");
		cliente.perform(get("/api/horarios")).andExpect(status().isUnauthorized());
		cliente.perform(get("/api/horarios").session(sesion))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
		CuentaTemporal ajena = nuevaCuenta();
		cliente.perform(get("/api/horarios/" + id(creado)).session(sesionDe(ajena)))
				.andExpect(status().isNotFound());
		cliente.perform(json(post("/api/horarios"), turno(lunes.plusDays(1), "08:00", "10:00"))
				.header("Origin", "https://ajeno.invalid")).andExpect(status().isForbidden());
		UUID ajeno = nuevoProfesional(ajena, especialidad);
		cliente.perform(json(post("/api/horarios"), turno(ajeno, consultorio, especialidad, lunes, "10:00", "12:00")))
				.andExpect(status().isNotFound());
	}

	@Test
	void creacionesConcurrentesDelMismoIntervaloSoloPersistenUna() throws Exception {
		CountDownLatch inicio = new CountDownLatch(1);
		try (var ejecutores = Executors.newFixedThreadPool(2)) {
			var primera = ejecutores.submit(() -> {
				assertTrue(inicio.await(10, TimeUnit.SECONDS));
				return cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "10:00")))
						.andReturn().getResponse().getStatus();
			});
			var segunda = ejecutores.submit(() -> {
				assertTrue(inicio.await(10, TimeUnit.SECONDS));
				return cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "10:00")))
						.andReturn().getResponse().getStatus();
			});
			inicio.countDown();
			List<Integer> estados = List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
			assertTrue(estados.containsAll(List.of(201, 409)), estados.toString());
		}
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
	}

	@Test
	void rechazaProgramacionRetroactivaYProtegeAsistenciaAlReabrirOCancelar() throws Exception {
		consultas.update("UPDATE availability SET valid_from_availability = ?::date WHERE id_professional = ?",
				lunes.minusWeeks(6).toString(), profesional);
		cliente.perform(json(post("/api/horarios"), turno(lunes.minusWeeks(5), "08:00", "10:00")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errores.fecha").exists());
		String creado = crearTurno(lunes, "08:00", "10:00");
		String aprobado = aprobar();
		consultas.update("""
				INSERT INTO attendance (id_attendance,id_institution,id_shift,id_recorder_user_account,status_attendance)
				VALUES (?,?,?,?,'pending')
				""", UUID.randomUUID(), cuenta.institucion(), id(creado), cuenta.usuario());
		cliente.perform(json(post("/api/horarios/reabrir"), accion("reabrir", aprobado))).andExpect(status().isConflict());
		cliente.perform(json(post("/api/horarios/cancelar"), accion("cancelar", aprobado))).andExpect(status().isConflict());
		assertEquals("approved", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?",
				String.class, idHorario(aprobado)));
	}

	@Test
	void catalogosNoPuedenInactivarRecursosQueUsaUnTurnoFuturo() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		aprobar();
		String datosProfesional = """
				{"nombres":"Temporal","apellidos":"Horarios","colegiatura":"%s","correo":"%s@medishift.test",
				"telefono":"","categoria":"Médico","estado":"Inactivo","especialidad":"Especialidad temporal %s"}
				""".formatted(profesional.toString().substring(0, 24), profesional, especialidad);
		cliente.perform(json(put("/api/profesionales/" + profesional), datosProfesional)).andExpect(status().isConflict());
		String datosSala = """
				{"codigo":"%s","nombre":"Sala temporal","ubicacion":"Piso 1","especialidad":"","estado":"Inactivo"}
				""".formatted("R-" + consultorio.toString().substring(0, 12));
		cliente.perform(json(put("/api/consultorios/" + consultorio), datosSala)).andExpect(status().isConflict());
		assertEquals("active", consultas.queryForObject("SELECT status_professional FROM professional WHERE id_professional = ?",
				String.class, profesional));
		assertEquals("active", consultas.queryForObject("SELECT status_room FROM room WHERE id_room = ?", String.class, consultorio));
	}
}
