package com.dextre.medishift.programacion.mensual;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persiste excepciones a la asistencia por defecto y conserva las marcaciones existentes. */
@Repository
public class RepositorioFaltas {

	private final JdbcTemplate consultas;

	public RepositorioFaltas(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public void marcarFalta(UUID institucion, UUID actor, UUID turno, UUID identificador) {
		consultas.update("""
				INSERT INTO attendance (id_attendance,id_institution,id_shift,id_recorder_user_account,status_attendance)
				VALUES (?,?,?,?,'absent') ON CONFLICT (id_shift) DO UPDATE
				SET status_attendance='absent',id_recorder_user_account=EXCLUDED.id_recorder_user_account
				WHERE attendance.id_institution=EXCLUDED.id_institution
				""", identificador, institucion, turno, actor);
	}

	public void restablecerAsistencia(UUID institucion, UUID actor, UUID turno) {
		// Sin entradas ni salidas reales, basta retirar la excepción. La auditoría mantiene su historial.
		consultas.update("""
				DELETE FROM attendance WHERE id_institution=? AND id_shift=? AND status_attendance='absent'
				AND check_in_at_attendance IS NULL AND check_out_at_attendance IS NULL
				""", institucion, turno);
		consultas.update("""
				UPDATE attendance SET status_attendance='present',id_recorder_user_account=?
				WHERE id_institution=? AND id_shift=? AND status_attendance='absent'
				""", actor, institucion, turno);
	}

	public void auditar(UUID institucion, UUID actor, UUID identificador, String antes, String despues) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
				entity_id_audit_event,action_audit_event,before_audit_event,after_audit_event)
				VALUES (?,?,?,'attendance',?,?,?::jsonb,?::jsonb)
				""", UUID.randomUUID(), institucion, actor, identificador,
				antes == null ? "create" : despues == null ? "delete" : "update", antes, despues);
	}
}
