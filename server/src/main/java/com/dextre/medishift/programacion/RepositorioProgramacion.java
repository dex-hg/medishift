package com.dextre.medishift.programacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Requisito;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Repository
public class RepositorioProgramacion {

	private static final String CONSULTA_TURNOS = """
			SELECT t.*, p.first_name_professional || ' ' || p.last_name_professional AS profesional,
			       r.code_room || ' · ' || r.name_room AS consultorio, e.name_specialty AS especialidad
			FROM shift t JOIN professional p ON p.id_professional = t.id_professional
			JOIN room r ON r.id_room = t.id_room JOIN specialty e ON e.id_specialty = t.id_specialty
			WHERE t.id_institution = ?
			""";
	private final JdbcTemplate consultas;

	public RepositorioProgramacion(JdbcTemplate consultas) { this.consultas = consultas; }

	public ZoneId zona(UUID institucion) {
		String nombre = consultas.query("SELECT time_zone_institution FROM institution WHERE id_institution = ?",
				(fila, numero) -> fila.getString(1), institucion).stream().findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
		try { return ZoneId.of(nombre); }
		catch (java.time.DateTimeException excepcion) {
			throw CatalogoException.conflicto("zonaHoraria", "La zona horaria de la institución debe corregirse antes de programar.");
		}
	}

	public Optional<Horario> operativo(UUID institucion, LocalDate inicio) {
		return consultas.query("""
				SELECT * FROM schedule WHERE id_institution = ? AND period_start_schedule = ?
				AND period_end_schedule = ? AND status_schedule IN ('draft','pending','approved','cancelled')
				ORDER BY version_schedule DESC LIMIT 1
				""", (fila, numero) -> leerHorario(fila), institucion, inicio, inicio.plusDays(6)).stream().findFirst();
	}

	public Optional<Horario> aprobado(UUID institucion, LocalDate inicio) {
		return consultas.query("""
				SELECT * FROM schedule WHERE id_institution = ? AND period_start_schedule = ?
				AND period_end_schedule = ? AND status_schedule = 'approved'
				""", (fila, numero) -> leerHorario(fila), institucion, inicio, inicio.plusDays(6)).stream().findFirst();
	}

	public Horario buscarHorario(UUID institucion, UUID identificador) {
		return consultas.query("SELECT * FROM schedule WHERE id_institution = ? AND id_schedule = ?",
				(fila, numero) -> leerHorario(fila), institucion, identificador).stream().findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
	}

	public Horario crearHorario(UUID institucion, UUID actor, LocalDate inicio) {
		if (inicio.plusDays(6).getYear() > 9999) {
			throw CatalogoException.conflicto("fecha", "Selecciona una semana completa dentro del año 9999.");
		}
		Integer version = consultas.queryForObject("""
				SELECT COALESCE(MAX(version_schedule), 0) + 1 FROM schedule
				WHERE id_institution = ? AND period_start_schedule = ? AND period_end_schedule = ?
				""", Integer.class, institucion, inicio, inicio.plusDays(6));
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule, id_institution, period_start_schedule, period_end_schedule,
				version_schedule, status_schedule, id_creator_user_account) VALUES (?, ?, ?, ?, ?, 'draft', ?)
				""", identificador, institucion, inicio, inicio.plusDays(6), version, actor);
		return buscarHorario(institucion, identificador);
	}

	public List<Turno> turnos(UUID institucion, UUID horario) {
		return consultas.query(CONSULTA_TURNOS + " AND t.id_schedule = ? ORDER BY t.start_at_shift, t.id_shift",
				(fila, numero) -> leerTurno(fila), institucion, horario);
	}

	public Turno buscarTurno(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA_TURNOS + " AND t.id_shift = ?", (fila, numero) -> leerTurno(fila),
				institucion, identificador).stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public List<Turno> futuros(UUID institucion, UUID profesional) {
		String condicion = profesional == null ? "" : " AND t.id_professional = ?";
		Object[] argumentos = profesional == null ? new Object[] { institucion } : new Object[] { institucion, profesional };
		return consultas.query(CONSULTA_TURNOS + """
				 AND t.end_at_shift > now() AND t.status_shift IN ('draft','pending','approved')
				""" + condicion, (fila, numero) -> leerTurno(fila), argumentos);
	}

	public List<Turno> aprobadosEn(UUID institucion, Instant inicio, Instant fin, UUID excluirHorario) {
		String excluir = excluirHorario == null ? "" : " AND t.id_schedule <> ?";
		Object[] argumentos = excluirHorario == null
				? new Object[] { institucion, Timestamp.from(inicio), Timestamp.from(fin) }
				: new Object[] { institucion, Timestamp.from(inicio), Timestamp.from(fin), excluirHorario };
		return consultas.query(CONSULTA_TURNOS + """
				 AND t.status_shift = 'approved' AND t.end_at_shift > ? AND t.start_at_shift < ?
				""" + excluir, (fila, numero) -> leerTurno(fila), argumentos);
	}

	public void insertarTurno(UUID institucion, Turno turno) {
		consultas.update("""
				INSERT INTO shift (id_shift, id_institution, id_schedule, id_professional, id_room,
				id_specialty, start_at_shift, end_at_shift, status_shift, note_shift)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?)
				""", turno.id(), institucion, turno.horario(), turno.profesional(), turno.consultorio(),
				turno.especialidad(), Timestamp.from(turno.inicio()), Timestamp.from(turno.fin()), turno.observacion());
	}

	public void actualizarTurno(UUID institucion, Turno turno) {
		int modificados = consultas.update("""
				UPDATE shift SET id_professional = ?, id_room = ?, id_specialty = ?,
				start_at_shift = ?, end_at_shift = ?, note_shift = ? WHERE id_institution = ? AND id_shift = ?
				AND status_shift = 'draft' AND EXISTS (SELECT 1 FROM schedule h
				WHERE h.id_schedule = shift.id_schedule AND h.status_schedule = 'draft')
				""", turno.profesional(), turno.consultorio(), turno.especialidad(), Timestamp.from(turno.inicio()),
				Timestamp.from(turno.fin()), turno.observacion(), institucion, turno.id());
		if (modificados != 1) {
			throw CatalogoException.conflicto("revision", "El turno cambió. Recarga el horario antes de editarlo.");
		}
	}

	public void eliminarTurno(UUID institucion, UUID turno) {
		int eliminados = consultas.update("""
				DELETE FROM shift WHERE id_institution = ? AND id_shift = ? AND status_shift = 'draft'
				AND EXISTS (SELECT 1 FROM schedule h WHERE h.id_schedule = shift.id_schedule AND h.status_schedule = 'draft')
				""", institucion, turno);
		if (eliminados != 1) {
			throw CatalogoException.conflicto("revision", "El turno cambió. Recarga el horario antes de eliminarlo.");
		}
	}

	public void verificarSinEjecucion(UUID institucion, UUID horario) {
		Boolean ejecutado = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM shift t WHERE t.id_institution = ? AND t.id_schedule = ?
				AND (t.start_at_shift <= clock_timestamp() OR EXISTS
				(SELECT 1 FROM attendance a WHERE a.id_institution = t.id_institution AND a.id_shift = t.id_shift)))
				""", Boolean.class, institucion, horario);
		if (Boolean.TRUE.equals(ejecutado)) {
			throw CatalogoException.conflicto("estado",
					"El horario contiene turnos ya iniciados o asistencia registrada. Su historial debe conservarse.");
		}
	}

	public void cambiarEstado(UUID institucion, UUID horario, String estado) {
		consultas.update("UPDATE shift SET status_shift = ? WHERE id_institution = ? AND id_schedule = ?"
				+ " AND status_shift <> 'cancelled'", estado, institucion, horario);
		consultas.update("UPDATE schedule SET status_schedule = ? WHERE id_institution = ? AND id_schedule = ?",
				estado, institucion, horario);
	}

	public void aprobar(UUID institucion, UUID horario, UUID actor) {
		consultas.update("""
				UPDATE schedule SET status_schedule = 'approved', id_approver_user_account = ?,
				approved_at_schedule = now() WHERE id_institution = ? AND id_schedule = ?
				""", actor, institucion, horario);
		consultas.update("""
				UPDATE shift SET status_shift = 'approved' WHERE id_institution = ?
				AND id_schedule = ? AND status_shift IN ('draft','pending')
				""", institucion, horario);
	}

	public void copiarContenido(UUID institucion, UUID anterior, UUID nuevo) {
		for (Turno turno : turnos(institucion, anterior)) {
			if (!"cancelled".equals(turno.estado())) {
				insertarTurno(institucion, new Turno(UUID.randomUUID(), nuevo, turno.profesional(), turno.consultorio(),
						turno.especialidad(), turno.inicio(), turno.fin(), "draft", turno.observacion(),
						turno.nombreProfesional(), turno.nombreConsultorio(), turno.nombreEspecialidad()));
			}
		}
		for (Requisito requisito : requisitos(anterior)) {
			consultas.update("""
					INSERT INTO coverage_requirement (id_coverage_requirement, id_schedule, id_specialty,
					start_at_coverage_requirement, end_at_coverage_requirement, required_count_coverage_requirement,
					note_coverage_requirement) VALUES (?, ?, ?, ?, ?, ?, ?)
					""", UUID.randomUUID(), nuevo, requisito.especialidad(), Timestamp.from(requisito.inicio()),
					Timestamp.from(requisito.fin()), requisito.cantidad(), requisito.observacion());
		}
	}

	public List<Requisito> requisitos(UUID horario) {
		return consultas.query("""
				SELECT * FROM coverage_requirement WHERE id_schedule = ?
				ORDER BY start_at_coverage_requirement, end_at_coverage_requirement, id_specialty
				""", (fila, numero) -> new Requisito(fila.getObject("id_specialty", UUID.class),
				fila.getTimestamp("start_at_coverage_requirement").toInstant(),
				fila.getTimestamp("end_at_coverage_requirement").toInstant(),
				fila.getInt("required_count_coverage_requirement"), fila.getString("note_coverage_requirement")), horario);
	}

	private Horario leerHorario(ResultSet fila) throws SQLException {
		Timestamp aprobado = fila.getTimestamp("approved_at_schedule");
		return new Horario(fila.getObject("id_schedule", UUID.class), fila.getObject("id_institution", UUID.class),
				fila.getDate("period_start_schedule").toLocalDate(), fila.getDate("period_end_schedule").toLocalDate(),
				fila.getInt("version_schedule"), fila.getString("status_schedule"),
				fila.getObject("id_creator_user_account", UUID.class), fila.getObject("id_approver_user_account", UUID.class),
				fila.getTimestamp("created_at_schedule").toInstant(), aprobado == null ? null : aprobado.toInstant());
	}

	private Turno leerTurno(ResultSet fila) throws SQLException {
		String observacion = fila.getString("note_shift");
		return new Turno(fila.getObject("id_shift", UUID.class), fila.getObject("id_schedule", UUID.class),
				fila.getObject("id_professional", UUID.class), fila.getObject("id_room", UUID.class),
				fila.getObject("id_specialty", UUID.class), fila.getTimestamp("start_at_shift").toInstant(),
				fila.getTimestamp("end_at_shift").toInstant(), fila.getString("status_shift"),
				observacion == null ? "" : observacion, fila.getString("profesional"),
				fila.getString("consultorio"), fila.getString("especialidad"));
	}

}
