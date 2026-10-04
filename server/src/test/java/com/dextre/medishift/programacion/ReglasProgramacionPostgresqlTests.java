package com.dextre.medishift.programacion;

import java.sql.Timestamp;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-horarios", matches = "true")
class ReglasProgramacionPostgresqlTests extends SoporteProgramacionPostgresql {

	@Test
	void seisHorasExactasSonValidasPeroUnMinutoAdicionalNo() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		crearTurno(lunes, "10:00", "14:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "14:00", "14:01")))
				.andExpect(status().isConflict());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
	}

	@Test
	void suma36HorasSemanalesYRechazaLaSeptimaJornada() throws Exception {
		for (int dia = 0; dia < 6; dia++) { crearTurno(lunes.plusDays(dia), "08:00", "14:00"); }
		cliente.perform(json(post("/api/horarios"), turno(lunes.plusDays(6), "08:00", "08:01")))
				.andExpect(status().isConflict());
		aprobar();
	}

	@Test
	void rechazaCrucesDeProfesionalYConsultorioInclusoEnBorrador() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		UUID otraSala = nuevoConsultorio(cuenta, true, especialidad);
		cliente.perform(json(post("/api/horarios"), turno(profesional, otraSala, especialidad, lunes, "09:59", "11:00")))
				.andExpect(status().isConflict());
		UUID otraPersona = nuevoProfesional(cuenta, especialidad);
		disponibilidad(otraPersona, 1, "00:00", "23:59");
		cliente.perform(json(post("/api/horarios"), turno(otraPersona, consultorio, especialidad, lunes, "09:59", "11:00")))
				.andExpect(status().isConflict());
		crearTurno(lunes, "10:00", "12:00");
	}

	@Test
	void disponibilidadContiguaCubreTodoElTurnoPeroUnHuecoNo() throws Exception {
		consultas.update("DELETE FROM availability WHERE id_professional = ?", profesional);
		disponibilidad(profesional, 1, "08:00", "10:00");
		UUID segunda = disponibilidad(profesional, 1, "10:01", "12:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "12:00")))
				.andExpect(status().isConflict());
		consultas.update("UPDATE availability SET start_time_availability = '10:00' WHERE id_availability = ?", segunda);
		crearTurno(lunes, "08:00", "12:00");
	}

	@Test
	void rechazaDiaIncorrectoVigenciaVencidaYDisponibilidadInactiva() throws Exception {
		consultas.update("DELETE FROM availability WHERE id_professional = ?", profesional);
		UUID ventana = disponibilidad(profesional, 2, "08:00", "12:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "12:00"))).andExpect(status().isConflict());
		consultas.update("UPDATE availability SET weekday_availability = 1, valid_to_availability = ?::date "
				+ "WHERE id_availability = ?", lunes.minusDays(1).toString(), ventana);
		cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "12:00"))).andExpect(status().isConflict());
		consultas.update("UPDATE availability SET valid_to_availability = ?::date, status_availability = 'inactive' "
				+ "WHERE id_availability = ?", lunes.toString(), ventana);
		cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "12:00"))).andExpect(status().isConflict());
	}

	@Test
	void competenciasSecundariasYSalasGeneralesSonValidas() throws Exception {
		UUID secundaria = nuevaEspecialidad();
		consultas.update("INSERT INTO professional_specialty VALUES (?, ?, false)", profesional, secundaria);
		cliente.perform(json(post("/api/horarios"), turno(profesional, consultorio, secundaria, lunes, "08:00", "10:00")))
				.andExpect(status().isCreated());
		UUID incompatible = nuevoConsultorio(cuenta, false, especialidad);
		cliente.perform(json(post("/api/horarios"), turno(profesional, incompatible, secundaria, lunes, "10:00", "12:00")))
				.andExpect(status().isConflict());
	}

	@Test
	void ausenciaAprobadaYBloqueoActivoImpidenGuardar() throws Exception {
		UUID ausencia = UUID.randomUUID();
		consultas.update("INSERT INTO professional_leave VALUES (?, ?, ?, ?, 'Temporal', 'approved')", ausencia,
				profesional, Timestamp.from(instante(lunes, "09:00")), Timestamp.from(instante(lunes, "10:00")));
		cliente.perform(json(post("/api/horarios"), turno(lunes, "08:00", "10:00"))).andExpect(status().isConflict());
		consultas.update("UPDATE professional_leave SET status_professional_leave = 'pending' WHERE id_professional_leave = ?", ausencia);
		consultas.update("INSERT INTO room_block VALUES (?, ?, ?, ?, 'Temporal', 'active')", UUID.randomUUID(),
				consultorio, Timestamp.from(instante(lunes, "10:00")), Timestamp.from(instante(lunes, "11:00")));
		crearTurno(lunes, "08:00", "10:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "10:00", "12:00"))).andExpect(status().isConflict());
	}

	@Test
	void aprobadoEnOtroPeriodoSeIncluyeEnCrucesYCargaDiaria() throws Exception {
		UUID legado = horarioLegado(lunes.minusDays(1), lunes.plusDays(3));
		turnoLegado(legado, instante(lunes, "08:00"), instante(lunes, "10:00"));
		cliente.perform(json(post("/api/horarios"), turno(lunes, "09:59", "11:00"))).andExpect(status().isConflict());
		crearTurno(lunes, "10:00", "14:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "14:00", "14:01"))).andExpect(status().isConflict());
	}

	@Test
	void turnoLegadoNocturnoSeRecortaAlDiaYALaSemanaLocal() throws Exception {
		UUID legado = horarioLegado(lunes.minusWeeks(1), lunes.minusDays(1));
		turnoLegado(legado, instante(lunes.minusDays(1), "23:00"), instante(lunes, "01:00"));
		crearTurno(lunes, "08:00", "13:00");
		cliente.perform(json(post("/api/horarios"), turno(lunes, "13:00", "13:01"))).andExpect(status().isConflict());
	}

	@Test
	void aprobarRevalidaBloqueosIntroducidosDespuesDelBorradorYRevierteTodo() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		String vista = semana();
		consultas.update("INSERT INTO room_block VALUES (?, ?, ?, ?, 'Temporal', 'active')", UUID.randomUUID(),
				consultorio, Timestamp.from(instante(lunes, "09:00")), Timestamp.from(instante(lunes, "10:00")));
		cliente.perform(json(post("/api/horarios/aprobar"), accion("aprobar", vista))).andExpect(status().isConflict());
		assertEquals("draft", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?",
				String.class, idHorario(vista)));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_institution = ? AND status_shift = 'approved'",
				Integer.class, cuenta.institucion()));
	}

	@Test
	void coberturaDebeSerSimultaneaDuranteCadaSegmento() throws Exception {
		crearTurno(lunes, "08:00", "10:00");
		crearTurno(lunes, "10:00", "12:00");
		UUID horario = idHorario(semana());
		consultas.update("INSERT INTO coverage_requirement VALUES (?, ?, ?, ?, ?, 2, 'Temporal')", UUID.randomUUID(),
				horario, especialidad, Timestamp.from(instante(lunes, "08:00")), Timestamp.from(instante(lunes, "12:00")));
		cliente.perform(json(post("/api/horarios/aprobar"), accion("aprobar", semana()))).andExpect(status().isConflict());
		UUID otro = nuevoProfesional(cuenta, especialidad);
		disponibilidad(otro, 1, "08:00", "12:00");
		UUID sala = nuevoConsultorio(cuenta, true, especialidad);
		cliente.perform(json(post("/api/horarios"), turno(otro, sala, especialidad, lunes, "08:00", "12:00")))
				.andExpect(status().isCreated());
		aprobar();
	}
}
