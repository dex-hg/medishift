package com.dextre.medishift.programacion.mensual;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudUnidad;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.UnidadPlanificacion;

@Repository
public class RepositorioPlanesMensuales {

	private static final String CONSULTA_PLAN = """
			SELECT h.*, i.time_zone_institution, to_jsonb(h)::text AS contenido_revision
			FROM schedule h JOIN institution i ON i.id_institution = h.id_institution
			WHERE h.id_institution = ? AND h.period_type_schedule = 'monthly'
			""";
	private final JdbcTemplate consultas;

	public RepositorioPlanesMensuales(JdbcTemplate consultas) { this.consultas = consultas; }

	public void verificarEsquema() {
		Boolean disponible = consultas.queryForObject("""
				SELECT to_regclass(current_schema() || '.planning_unit') IS NOT NULL AND EXISTS (
				 SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
				 AND table_name = 'schedule' AND column_name = 'period_type_schedule')
				""", Boolean.class);
		if (!Boolean.TRUE.equals(disponible)) {
			throw CatalogoException.noDisponible("El núcleo mensual aún no está instalado. Contacta al administrador de la instalación.");
		}
	}

	public List<UnidadPlanificacion> unidades(UUID institucion) {
		return consultas.query("SELECT * FROM planning_unit WHERE id_institution = ? ORDER BY code_planning_unit, id_planning_unit",
				(fila, numero) -> leerUnidad(fila), institucion);
	}

	public UnidadPlanificacion buscarUnidad(UUID institucion, UUID identificador) {
		return consultas.query("SELECT * FROM planning_unit WHERE id_institution = ? AND id_planning_unit = ?",
				(fila, numero) -> leerUnidad(fila), institucion, identificador).stream().findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
	}

	public UnidadPlanificacion crearUnidad(UUID institucion, SolicitudUnidad solicitud) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO planning_unit (id_planning_unit,id_institution,code_planning_unit,
				name_planning_unit,type_planning_unit,status_planning_unit) VALUES (?,?,?,?,?,'active')
				""", identificador, institucion, solicitud.codigo(), solicitud.nombre(),
				"Departamento".equals(solicitud.tipo()) ? "department" : "service");
		return buscarUnidad(institucion, identificador);
	}

	public List<PlanMensual> planes(UUID institucion, UUID unidad, PeriodoMensual periodo) {
		return consultas.query(CONSULTA_PLAN + """
				 AND h.id_planning_unit = ? AND h.period_start_schedule = ? ORDER BY h.version_schedule DESC
				""", (fila, numero) -> leerPlan(fila), institucion, unidad, periodo.inicio());
	}

	public PlanMensual buscarPlan(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA_PLAN + " AND h.id_schedule = ?", (fila, numero) -> leerPlan(fila),
				institucion, identificador).stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	/** El servicio serializa la consulta de versiones y la inserción con el bloqueo institucional. */
	public PlanMensual crearBorrador(UUID institucion, UUID actor, UUID unidad, PeriodoMensual periodo) {
		Boolean existe = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM schedule WHERE id_institution = ? AND id_planning_unit = ?
				AND period_type_schedule = 'monthly' AND period_start_schedule = ?
				AND status_schedule IN ('draft','pending','approved'))
				""", Boolean.class, institucion, unidad, periodo.inicio());
		if (Boolean.TRUE.equals(existe)) {
			throw CatalogoException.conflicto("periodo", "Ya existe una versión operativa para esta unidad y mes. Consulta el plan existente.");
		}
		Long anterior = consultas.queryForObject("""
				SELECT COALESCE(MAX(version_schedule), 0)::bigint FROM schedule WHERE id_institution = ?
				AND id_planning_unit = ? AND period_type_schedule = 'monthly' AND period_start_schedule = ?
				""", Long.class, institucion, unidad, periodo.inicio());
		if (anterior == null || anterior >= Integer.MAX_VALUE) {
			throw CatalogoException.conflicto("version", "No se puede crear otra versión de este mes.");
		}
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule,id_institution,period_start_schedule,period_end_schedule,
				version_schedule,status_schedule,id_creator_user_account,period_type_schedule,id_planning_unit)
				VALUES (?,?,?,?,?,'draft',?,'monthly',?)
				""", identificador, institucion, periodo.inicio(), periodo.fin(), anterior.intValue() + 1, actor, unidad);
		return buscarPlan(institucion, identificador);
	}

	public void registrarCreacion(UUID institucion, UUID actor, UUID identificador, boolean unidad) {
		String tabla = unidad ? "planning_unit" : "schedule";
		String columna = unidad ? "id_planning_unit" : "id_schedule";
		int registrados = consultas.update("""
				INSERT INTO audit_event (id_audit_event,id_institution,id_actor_user_account,
				entity_name_audit_event,entity_id_audit_event,action_audit_event,after_audit_event)
				SELECT ?, ?, ?, ?, ?, 'create', to_jsonb(origen) FROM %s origen
				WHERE origen.id_institution = ? AND origen.%s = ?
				""".formatted(tabla, columna), UUID.randomUUID(), institucion, actor, tabla, identificador, institucion, identificador);
		if (registrados != 1) { throw CatalogoException.noEncontrado(); }
	}

	private UnidadPlanificacion leerUnidad(ResultSet fila) throws SQLException {
		return new UnidadPlanificacion(fila.getObject("id_planning_unit", UUID.class), fila.getString("code_planning_unit"),
				fila.getString("name_planning_unit"), "department".equals(fila.getString("type_planning_unit")) ? "Departamento" : "Servicio",
				"active".equals(fila.getString("status_planning_unit")) ? "Activo" : "Inactivo");
	}

	private PlanMensual leerPlan(ResultSet fila) throws SQLException {
		LocalDate desde = fila.getObject("period_start_schedule", LocalDate.class);
		String zona = fila.getString("time_zone_institution");
		Timestamp aprobado = fila.getTimestamp("approved_at_schedule");
		return new PlanMensual(fila.getObject("id_schedule", UUID.class), fila.getObject("id_planning_unit", UUID.class),
				desde.toString().substring(0, 7), desde, fila.getObject("period_end_schedule", LocalDate.class), zona,
				fila.getInt("version_schedule"), estado(fila.getString("status_schedule")),
				fila.getObject("id_creator_user_account", UUID.class), fila.getTimestamp("created_at_schedule").toInstant(),
				fila.getObject("id_approver_user_account", UUID.class), aprobado == null ? null : aprobado.toInstant(),
				resumir(fila.getString("contenido_revision") + "|" + zona));
	}

	private String estado(String valor) {
		return switch (valor) {
			case "draft" -> "Borrador";
			case "pending" -> "Pendiente";
			case "approved" -> "Aprobado";
			case "superseded" -> "Sustituido";
			case "cancelled" -> "Cancelado";
			default -> throw new IllegalStateException("Estado mensual desconocido.");
		};
	}

	private String resumir(String contenido) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8))); }
		catch (NoSuchAlgorithmException fallo) { throw new IllegalStateException("El entorno no admite SHA-256.", fallo); }
	}
}
