package com.dextre.medishift.programacion.mensual;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.ConsultorioArea;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.Area;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.RegistroTurno;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudConsultorio;

@Repository
public class RepositorioPlanificador {

	private static final String CONSULTA_TURNOS = """
			SELECT t.*, h.id_planning_unit, h.status_schedule,
			p.first_name_professional || ' ' || p.last_name_professional AS profesional,
			r.code_room || ' · ' || r.name_room AS consultorio, e.name_specialty AS especialidad
			FROM shift t JOIN schedule h ON h.id_schedule = t.id_schedule AND h.id_institution = t.id_institution
			JOIN professional p ON p.id_professional = t.id_professional
			JOIN room r ON r.id_room = t.id_room JOIN specialty e ON e.id_specialty = t.id_specialty
			WHERE t.id_institution = ?
			""";
	private final JdbcTemplate consultas;
	private final RepositorioRecursosProgramacion recursos;

	public RepositorioPlanificador(JdbcTemplate consultas, RepositorioRecursosProgramacion recursos) {
		this.consultas = consultas;
		this.recursos = recursos;
	}

	public void verificarEsquema() {
		Boolean disponible = consultas.queryForObject("""
				SELECT count(*) = 3 FROM information_schema.columns WHERE table_schema = current_schema()
				AND ((table_name = 'room' AND column_name = 'id_planning_unit')
				OR (table_name = 'planning_unit' AND column_name = 'id_specialty')
				OR (table_name = 'specialty' AND column_name = 'id_institution'))
				""", Boolean.class);
		if (!Boolean.TRUE.equals(disponible)) {
			throw CatalogoException.noDisponible("El calendario por especialidades requiere instalar la migración V003.");
		}
	}

	public List<ConsultorioArea> consultorios(UUID institucion) {
		Map<UUID, Vinculo> areas = consultas.query("""
				SELECT r.id_room,r.id_planning_unit,u.id_specialty FROM room r LEFT JOIN planning_unit u
				ON u.id_planning_unit=r.id_planning_unit AND u.id_institution=r.id_institution WHERE r.id_institution=?
				""", (fila, numero) -> new Vinculo(fila.getObject(1, UUID.class), fila.getObject(2, UUID.class),
				fila.getObject(3, UUID.class)), institucion).stream().collect(Collectors.toMap(Vinculo::consultorio, vinculo -> vinculo));
		return recursos.listar(institucion).consultorios().stream().map(consultorio -> new ConsultorioArea(consultorio.id(),
				consultorio.codigo(), consultorio.nombre(), areas.get(consultorio.id()).area(),
				areas.get(consultorio.id()).especialidad(), consultorio.estado(),
				consultorio.usoGeneral(), consultorio.especialidades())).toList();
	}

	public List<Area> areas(UUID institucion) {
		return consultas.query("""
				SELECT u.*,e.name_specialty FROM planning_unit u LEFT JOIN specialty e ON e.id_specialty=u.id_specialty
				WHERE u.id_institution=? ORDER BY lower(u.name_planning_unit),u.id_planning_unit
				""", (fila, numero) -> new Area(fila.getObject("id_planning_unit", UUID.class), fila.getString("code_planning_unit"),
				fila.getString("name_planning_unit"), "active".equals(fila.getString("status_planning_unit")) ? "Activo" : "Inactivo",
				fila.getObject("id_specialty", UUID.class), fila.getString("name_specialty") == null ? "" : fila.getString("name_specialty")), institucion);
	}

	public Area buscarArea(UUID institucion, UUID identificador) {
		return areas(institucion).stream().filter(area -> area.id().equals(identificador)).findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public void configurarArea(UUID institucion, UUID area, UUID especialidad) {
		Boolean incompatible = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM shift t JOIN room r ON r.id_room=t.id_room AND r.id_institution=t.id_institution
				JOIN schedule h ON h.id_schedule=t.id_schedule AND h.id_institution=t.id_institution
				WHERE t.id_institution=? AND r.id_planning_unit=? AND t.end_at_shift>now() AND t.id_specialty<>?
				AND t.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved'))
				OR EXISTS (SELECT 1 FROM room r WHERE r.id_institution=? AND r.id_planning_unit=? AND NOT r.is_general_room
				AND NOT EXISTS (SELECT 1 FROM room_specialty re WHERE re.id_room=r.id_room AND re.id_specialty=?))
				""", Boolean.class, institucion, area, especialidad, institucion, area, especialidad);
		if (Boolean.TRUE.equals(incompatible)) {
			throw CatalogoException.conflicto("idEspecialidad", "Hay consultorios o turnos futuros de otra especialidad en esta área. Reubícalos o corrígelos antes de cambiarla.");
		}
		if (consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_institution=? AND id_planning_unit=?",
				especialidad, institucion, area) != 1) { throw CatalogoException.noEncontrado(); }
	}

	public String capturaArea(UUID institucion, UUID identificador) {
		return consultas.queryForObject("SELECT to_jsonb(u)::text FROM planning_unit u WHERE id_institution=? AND id_planning_unit=?",
				String.class, institucion, identificador);
	}

	public void auditarArea(UUID institucion, UUID actor, UUID identificador, String antes) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
				entity_id_audit_event,action_audit_event,before_audit_event,after_audit_event) VALUES (?,?,?,'planning_unit',?,'update',?::jsonb,?::jsonb)
				""", UUID.randomUUID(), institucion, actor, identificador, antes, capturaArea(institucion, identificador));
	}

	public ConsultorioArea buscarConsultorio(UUID institucion, UUID identificador) {
		return consultorios(institucion).stream().filter(consultorio -> consultorio.id().equals(identificador)).findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
	}

	public ConsultorioArea crearConsultorio(UUID institucion, SolicitudConsultorio solicitud) {
		Boolean duplicado = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM room WHERE id_institution = ? AND lower(btrim(code_room)) = lower(?))
				""", Boolean.class, institucion, solicitud.codigo());
		if (Boolean.TRUE.equals(duplicado)) { throw CatalogoException.conflicto("codigo", "Ya existe un consultorio con ese código."); }
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO room (id_room,id_institution,code_room,name_room,location_room,is_general_room,
				status_room,id_planning_unit) VALUES (?,?,?,?,?,true,'active',?)
				""", identificador, institucion, solicitud.codigo(), solicitud.nombre(), solicitud.ubicacion(), solicitud.idArea());
		return buscarConsultorio(institucion, identificador);
	}

	public void vincularConsultorio(UUID institucion, UUID identificador, UUID area) {
		buscarConsultorio(institucion, identificador);
		Boolean admite = consultas.queryForObject("""
				SELECT r.is_general_room OR EXISTS (SELECT 1 FROM room_specialty re WHERE re.id_room=r.id_room AND re.id_specialty=u.id_specialty)
				FROM room r JOIN planning_unit u ON u.id_planning_unit=? AND u.id_institution=r.id_institution
				WHERE r.id_institution=? AND r.id_room=?
				""", Boolean.class, area, institucion, identificador);
		if (!Boolean.TRUE.equals(admite)) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio no admite la especialidad de esta área.");
		}
		Boolean turnosIncompatibles = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM shift t JOIN schedule h ON h.id_schedule=t.id_schedule
				AND h.id_institution=t.id_institution JOIN planning_unit u ON u.id_planning_unit=?
				AND u.id_institution=t.id_institution WHERE t.id_institution=? AND t.id_room=?
				AND t.end_at_shift>now() AND t.id_specialty<>u.id_specialty
				AND t.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved'))
				""", Boolean.class, area, institucion, identificador);
		if (Boolean.TRUE.equals(turnosIncompatibles)) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio tiene turnos futuros de otra especialidad. Reubícalos antes de vincularlo.");
		}
		Boolean incompatible = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM shift t JOIN schedule h ON h.id_schedule = t.id_schedule
				AND h.id_institution = t.id_institution WHERE t.id_institution = ? AND t.id_room = ?
				AND h.period_type_schedule = 'monthly' AND h.id_planning_unit <> ?)
				""", Boolean.class, institucion, identificador, area);
		if (Boolean.TRUE.equals(incompatible)) {
			throw CatalogoException.conflicto("idArea", "El consultorio tiene turnos mensuales en otra área. Conserva su carpeta para proteger el historial.");
		}
		consultas.update("UPDATE room SET id_planning_unit = ? WHERE id_institution = ? AND id_room = ?",
				area, institucion, identificador);
	}

	public List<RegistroTurno> turnos(UUID institucion, PeriodoMensual periodo) {
		return consultas.query(CONSULTA_TURNOS + """
				 AND h.period_type_schedule = 'monthly' AND h.period_start_schedule = ?
				 AND h.status_schedule IN ('draft','pending','approved')
				 AND t.status_shift IN ('draft','pending','approved') ORDER BY t.start_at_shift,t.id_shift
				""", (fila, numero) -> leerTurno(fila), institucion, periodo.inicio());
	}

	public RegistroTurno buscarTurno(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA_TURNOS + " AND h.period_type_schedule = 'monthly' AND t.id_shift = ?",
				(fila, numero) -> leerTurno(fila), institucion, identificador).stream().findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
	}

	/** Incluye las semanas vecinas y todos los planes operativos de la institución. */
	public List<Turno> vecinos(UUID institucion, Instant inicio, Instant fin, UUID excluir) {
		return consultas.query(CONSULTA_TURNOS + """
				 AND h.status_schedule IN ('draft','pending','approved') AND t.status_shift IN ('draft','pending','approved')
				 AND t.start_at_shift < ? AND t.end_at_shift > ? AND t.id_shift <> ?
				""", (fila, numero) -> leerTurno(fila).turno(), institucion, Timestamp.from(fin), Timestamp.from(inicio), excluir);
	}

	public void insertar(UUID institucion, Turno turno) {
		consultas.update("""
				INSERT INTO shift (id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
				start_at_shift,end_at_shift,status_shift,note_shift) VALUES (?,?,?,?,?,?,?,?,'draft',?)
				""", turno.id(), institucion, turno.horario(), turno.profesional(), turno.consultorio(), turno.especialidad(),
				Timestamp.from(turno.inicio()), Timestamp.from(turno.fin()), turno.observacion());
	}

	public void actualizar(UUID institucion, Turno turno) {
		int cantidad = consultas.update("""
				UPDATE shift SET id_schedule=?,id_professional=?,id_room=?,id_specialty=?,start_at_shift=?,
				end_at_shift=?,note_shift=? WHERE id_institution=? AND id_shift=? AND status_shift='draft'
				""", turno.horario(), turno.profesional(), turno.consultorio(), turno.especialidad(), Timestamp.from(turno.inicio()),
				Timestamp.from(turno.fin()), turno.observacion(), institucion, turno.id());
		if (cantidad != 1) { throw CatalogoException.conflicto("revision", "El turno cambió. Recarga el calendario."); }
	}

	public void eliminar(UUID institucion, UUID identificador) {
		if (consultas.update("DELETE FROM shift WHERE id_institution=? AND id_shift=? AND status_shift='draft'",
				institucion, identificador) != 1) {
			throw CatalogoException.conflicto("revision", "El turno cambió. Recarga el calendario.");
		}
	}

	public boolean tieneAsistencia(UUID institucion, UUID identificador) {
		return Boolean.TRUE.equals(consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM attendance WHERE id_institution = ? AND id_shift = ?)
				""", Boolean.class, institucion, identificador));
	}

	public String captura(UUID institucion, UUID identificador, boolean consultorio) {
		String tabla = consultorio ? "room" : "shift";
		String columna = consultorio ? "id_room" : "id_shift";
		return consultas.queryForObject("SELECT to_jsonb(registro)::text FROM " + tabla
				+ " registro WHERE id_institution = ? AND " + columna + " = ?", String.class, institucion, identificador);
	}

	public void auditar(UUID institucion, UUID actor, UUID identificador, boolean consultorio,
			String accion, String antes, String despues) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
				entity_id_audit_event,action_audit_event,before_audit_event,after_audit_event) VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb)
				""", UUID.randomUUID(), institucion, actor, consultorio ? "room" : "shift", identificador, accion, antes, despues);
	}

	private RegistroTurno leerTurno(ResultSet fila) throws SQLException {
		String observacion = fila.getString("note_shift");
		Turno turno = new Turno(fila.getObject("id_shift", UUID.class), fila.getObject("id_schedule", UUID.class),
				fila.getObject("id_professional", UUID.class), fila.getObject("id_room", UUID.class),
				fila.getObject("id_specialty", UUID.class), fila.getTimestamp("start_at_shift").toInstant(),
				fila.getTimestamp("end_at_shift").toInstant(), fila.getString("status_shift"), observacion == null ? "" : observacion,
				fila.getString("profesional"), fila.getString("consultorio"), fila.getString("especialidad"));
		return new RegistroTurno(turno, fila.getObject("id_planning_unit", UUID.class), fila.getString("status_schedule"));
	}

	private record Vinculo(UUID consultorio, UUID area, UUID especialidad) { }
}
