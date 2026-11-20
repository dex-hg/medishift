package com.dextre.medishift.programacion;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.catalogos.RepositorioProfesionales;
import com.dextre.medishift.programacion.DatosProgramacion.ConsultorioRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.ProfesionalRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.Recursos;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Repository
public class RepositorioRecursosProgramacion {
	private static final String ACEPTA_CONSULTORIO = """
			((r.is_general_room AND r.id_planning_unit IS NULL) OR EXISTS (
			    SELECT 1 FROM etiqueta_consultorio_programable v
			    WHERE (v.id_institution,v.id_room,v.id_specialty)=(r.id_institution,r.id_room,e.id_specialty)
			))
			""";
	private static final String COMPETENCIA_ACTIVA = """
			(e.activa AND e.id_institution=p.id_institution AND EXISTS (
			    SELECT 1 FROM professional_specialty v
			    WHERE (v.id_professional,v.id_specialty)=(p.id_professional,e.id_specialty) AND v.activo
			))
			""";
	private final JdbcTemplate consultas;
	private final RepositorioProgramacion horarios;
	private final RepositorioProfesionales profesionales;

	public RepositorioRecursosProgramacion(JdbcTemplate consultas, RepositorioProgramacion horarios,
			RepositorioProfesionales profesionales) {
		this.consultas = consultas;
		this.horarios = horarios;
		this.profesionales = profesionales;
	}

	public Recursos listar(UUID institucion) {
		return new Recursos(horarios.zona(institucion).getId(), profesionales(institucion), consultorios(institucion));
	}

	public List<ProfesionalRecurso> profesionales(UUID institucion) {
		return profesionales.listar(institucion).stream().map(persona -> new ProfesionalRecurso(persona.id(),
				persona.nombres() + " " + persona.apellidos(), persona.colegiatura(), persona.estado(),
				persona.etiquetas().stream().map(etiqueta -> new EspecialidadRecurso(etiqueta.id(), etiqueta.nombre())).toList())).toList();
	}

	public List<ConsultorioRecurso> consultorios(UUID institucion) {
		return consultas.query("""
				SELECT r.*, etiquetas.* FROM room r
				LEFT JOIN LATERAL (
				    SELECT array_agg(v.id_specialty ORDER BY lower(v.name_specialty),v.id_specialty) AS ids_etiquetas,
				           array_agg(v.name_specialty ORDER BY lower(v.name_specialty),v.id_specialty) AS nombres_etiquetas
				    FROM etiqueta_consultorio_programable v
				    WHERE (v.id_institution,v.id_room)=(r.id_institution,r.id_room)
				) etiquetas ON true WHERE r.id_institution=?
				ORDER BY lower(r.code_room),r.id_room
				""", (fila, numero) -> {
			UUID area = fila.getObject("id_planning_unit", UUID.class);
			return new ConsultorioRecurso(fila.getObject("id_room", UUID.class), fila.getString("code_room"),
					fila.getString("name_room"), "active".equals(fila.getString("status_room")) ? "Activo" : "Inactivo",
					fila.getBoolean("is_general_room") && area == null, RepositorioEspecialidades.leerReferencias(fila).stream()
						.map(etiqueta -> new EspecialidadRecurso(etiqueta.id(), etiqueta.nombre())).toList(), area);
		}, institucion);
	}

	public void validar(UUID institucion, Turno turno) {
		validar(institucion, turno, null);
	}

	public void validar(UUID institucion, Turno turno, UUID unidad) {
		var compatibilidad = consultas.query("""
				SELECT p.status_professional='active' AS medico_activo,r.status_room='active' AS sala_activa,
				       coalesce(%s,false) AS competente, %s AS compatible,r.id_planning_unit
				FROM professional p JOIN room r USING (id_institution)
				LEFT JOIN specialty e ON e.id_specialty=?
				WHERE p.id_institution=? AND p.id_professional=? AND r.id_room=?
				""".formatted(COMPETENCIA_ACTIVA, ACEPTA_CONSULTORIO),
				(fila, numero) -> new Compatibilidad(fila.getBoolean("medico_activo"), fila.getBoolean("sala_activa"),
						fila.getBoolean("competente"), fila.getBoolean("compatible"), fila.getObject("id_planning_unit", UUID.class)),
				turno.especialidad(), institucion, turno.profesional(), turno.consultorio()).stream().findFirst()
				.orElseThrow(CatalogoException::noEncontrado);
		if (!compatibilidad.medicoActivo()) throw CatalogoException.conflicto("idProfesional", "El profesional está inactivo.");
		if (!compatibilidad.salaActiva()) throw CatalogoException.conflicto("idConsultorio", "El consultorio está inactivo.");
		if (unidad != null && !unidad.equals(compatibilidad.area())) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio debe pertenecer al área o subárea elegida.");
		}
		if (!compatibilidad.competente()) {
			throw CatalogoException.conflicto("idEspecialidad", "La etiqueta no está activa para este profesional.");
		}
		if (!compatibilidad.compatible()) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio o su unidad no admite la etiqueta seleccionada.");
		}
	}

	public void exigirTurnosCompatibles(UUID institucion, Alcance alcance, UUID identificador, String campo) {
		var afectados = consultas.query("""
				SELECT count(*) OVER () AS cantidad, (t.start_at_shift AT TIME ZONE i.time_zone_institution)::date AS fecha,
				       coalesce(u.name_planning_unit,'Semana sin área') AS unidad
				FROM shift t JOIN schedule h USING (id_institution,id_schedule)
				JOIN institution i USING (id_institution)
				JOIN professional p USING (id_institution,id_professional)
				JOIN room r USING (id_institution,id_room) JOIN specialty e ON e.id_specialty=t.id_specialty
				LEFT JOIN planning_unit u ON u.id_institution=t.id_institution
				    AND u.id_planning_unit=coalesce(h.id_planning_unit,r.id_planning_unit)
				WHERE t.id_institution=? AND %s=? AND t.end_at_shift>now()
				AND t.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved')
				AND (p.status_professional<>'active' OR r.status_room<>'active'
				    OR NOT %s OR NOT %s
				    OR (h.id_planning_unit IS NOT NULL AND h.id_planning_unit IS DISTINCT FROM r.id_planning_unit))
				ORDER BY t.start_at_shift,t.id_shift LIMIT 4
				""".formatted(alcance.columna, COMPETENCIA_ACTIVA, ACEPTA_CONSULTORIO),
				(fila, numero) -> new Afectado(fila.getLong("cantidad"), fila.getString("unidad") + " · " + fila.getString("fecha")),
				institucion, identificador);
		if (!afectados.isEmpty()) {
			String ejemplos = String.join(", ", afectados.stream().map(Afectado::detalle).distinct().toList());
			throw CatalogoException.conflicto(campo, "Quedarían incompatibles " + afectados.getFirst().cantidad()
					+ " turnos vigentes (" + ejemplos + "). Reubícalos o cambia sus etiquetas antes de guardar.");
		}
	}

	public void exigirConsultorioCompatible(UUID institucion, UUID consultorio, String campo) {
		exigirConsultoriosCompatibles(institucion, "r.id_room", consultorio, campo);
	}

	public void exigirConsultoriosDelAreaCompatibles(UUID institucion, UUID unidad, String campo) {
		exigirConsultoriosCompatibles(institucion, "r.id_planning_unit", unidad, campo);
	}

	private void exigirConsultoriosCompatibles(UUID institucion, String columna, UUID identificador, String campo) {
		List<String> incompatibles = consultas.query("""
				SELECT r.code_room FROM room r JOIN planning_unit u USING (id_institution,id_planning_unit)
				WHERE r.id_institution=? AND %s=? AND NOT r.is_general_room
				  AND r.status_room='active' AND u.status_planning_unit='active'
				  AND NOT EXISTS (SELECT 1 FROM etiqueta_consultorio_programable v
				      WHERE (v.id_institution,v.id_room)=(r.id_institution,r.id_room))
				ORDER BY r.code_room LIMIT 4
				""".formatted(columna), (fila, numero) -> fila.getString(1), institucion, identificador);
		if (!incompatibles.isEmpty()) {
			throw CatalogoException.conflicto(campo, "Estos consultorios requieren una etiqueta en común con su área: "
					+ String.join(", ", incompatibles) + ".");
		}
	}

	public void exigirEtiquetaSinCobertura(UUID institucion, UUID etiqueta) {
		Boolean requerida = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM coverage_requirement c JOIN schedule h USING (id_schedule)
				WHERE h.id_institution=? AND c.id_specialty=? AND c.end_at_coverage_requirement>now()
				AND h.status_schedule IN ('draft','pending','approved'))
				""", Boolean.class, institucion, etiqueta);
		if (Boolean.TRUE.equals(requerida)) {
			throw CatalogoException.conflicto("activa", "La etiqueta todavía tiene requisitos de cobertura vigentes.");
		}
	}

	public enum Alcance {
		PROFESIONAL("t.id_professional"), CONSULTORIO("t.id_room"),
		AREA("coalesce(h.id_planning_unit,r.id_planning_unit)"), ETIQUETA("t.id_specialty");

		private final String columna;
		Alcance(String columna) { this.columna = columna; }
	}

	private record Compatibilidad(boolean medicoActivo, boolean salaActiva, boolean competente, boolean compatible, UUID area) { }
	private record Afectado(long cantidad, String detalle) { }
}
