package com.dextre.medishift.programacion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a los datos que restringen la asignación de turnos. */
@Repository
public class RepositorioRestriccionesProgramacion {

	private final JdbcTemplate consultas;

	public RepositorioRestriccionesProgramacion(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public boolean tieneAusenciaAprobada(UUID profesional, Instant inicio, Instant fin) {
		Boolean ausencia = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM professional_leave WHERE id_professional = ?
				AND status_professional_leave = 'approved' AND start_at_professional_leave < ? AND end_at_professional_leave > ?)
				""", Boolean.class, profesional, Timestamp.from(fin), Timestamp.from(inicio));
		return Boolean.TRUE.equals(ausencia);
	}

	public boolean tieneBloqueoActivo(UUID consultorio, Instant inicio, Instant fin) {
		Boolean bloqueo = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM room_block WHERE id_room = ? AND status_room_block = 'active'
				AND start_at_room_block < ? AND end_at_room_block > ?)
				""", Boolean.class, consultorio, Timestamp.from(fin), Timestamp.from(inicio));
		return Boolean.TRUE.equals(bloqueo);
	}

}
