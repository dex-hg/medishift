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
				SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
				AND table_name = 'room' AND column_name = 'id_planning_unit')
				""", Boolean.class);
		if (!Boolean.TRUE.equals(disponible)) {
			throw CatalogoException.noDisponible("El calendario por áreas requiere instalar la migración V002.");
		}
	}

	public List<ConsultorioArea> consultorios(UUID institucion) {
		Map<UUID, UUID> areas = consultas.query("SELECT id_room,id_planning_unit FROM room WHERE id_institution = ?",
				(fila, numero) -> new Vinculo(fila.getObject(1, UUID.class), fila.getObject(2, UUID.class)), institucion)
				.stream().filter(vinculo -> vinculo.area() != null).collect(Collectors.toMap(Vinculo::consultorio, Vinculo::area));
		return recursos.listar(institucion).consultorios().stream().map(consultorio -> new ConsultorioArea(consultorio.id(),
				consultorio.codigo(), consultorio.nombre(), areas.get(consultorio.id()), consultorio.estado(),
				consultorio.usoGeneral(), consultorio.especialidades())).toList();
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

	private record Vinculo(UUID consultorio, UUID area) { }
}
