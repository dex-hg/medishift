package com.dextre.medishift.programacion;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.ConsultorioRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.ProfesionalRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.Recursos;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Repository
public class RepositorioRecursosProgramacion {

	private final JdbcTemplate consultas;
	private final RepositorioProgramacion horarios;

	public RepositorioRecursosProgramacion(JdbcTemplate consultas, RepositorioProgramacion horarios) {
		this.consultas = consultas;
		this.horarios = horarios;
	}

	public Recursos listar(UUID institucion) {
		List<ProfesionalRecurso> profesionales = consultas.query("""
				SELECT id_professional, first_name_professional || ' ' || last_name_professional AS nombre,
				license_number_professional, status_professional FROM professional WHERE id_institution = ?
				ORDER BY lower(last_name_professional), lower(first_name_professional), id_professional
				""", (fila, numero) -> new ProfesionalRecurso(fila.getObject("id_professional", UUID.class),
				fila.getString("nombre"), fila.getString("license_number_professional"), estado(fila.getString("status_professional")),
				especialidadesProfesional(fila.getObject("id_professional", UUID.class))), institucion);
		List<ConsultorioRecurso> consultorios = consultas.query("""
				SELECT r.*,u.id_specialty AS especialidad_area,e.name_specialty AS nombre_especialidad_area
				FROM room r LEFT JOIN planning_unit u ON u.id_planning_unit=r.id_planning_unit AND u.id_institution=r.id_institution
				LEFT JOIN specialty e ON e.id_specialty=u.id_specialty
				WHERE r.id_institution = ? ORDER BY lower(r.code_room), r.id_room
				""", (fila, numero) -> new ConsultorioRecurso(fila.getObject("id_room", UUID.class),
				fila.getString("code_room"), fila.getString("name_room"), estado(fila.getString("status_room")),
				fila.getBoolean("is_general_room") && fila.getObject("especialidad_area") == null,
				especialidadesPermitidas(fila.getObject("id_room", UUID.class), fila.getBoolean("is_general_room"),
						fila.getObject("especialidad_area", UUID.class), fila.getString("nombre_especialidad_area"))), institucion);
		return new Recursos(horarios.zona(institucion).getId(), profesionales, consultorios);
	}

	public void validar(UUID institucion, Turno turno) {
		List<String> profesionales = consultas.query("""
				SELECT status_professional FROM professional WHERE id_institution = ? AND id_professional = ?
				""", (fila, numero) -> fila.getString(1), institucion, turno.profesional());
		List<Boolean> consultorios = consultas.query("""
				SELECT status_room = 'active' FROM room WHERE id_institution = ? AND id_room = ?
				""", (fila, numero) -> fila.getBoolean(1), institucion, turno.consultorio());
		if (profesionales.isEmpty() || consultorios.isEmpty()) { throw CatalogoException.noEncontrado(); }
		if (!"active".equals(profesionales.getFirst())) {
			throw CatalogoException.conflicto("idProfesional", "El profesional está inactivo. Revisa los turnos vinculados.");
		}
		if (!consultorios.getFirst()) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio está inactivo. Revisa los turnos vinculados.");
		}
		Boolean competente = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM professional_specialty WHERE id_professional = ? AND id_specialty = ?)
				""", Boolean.class, turno.profesional(), turno.especialidad());
		if (!Boolean.TRUE.equals(competente)) {
			throw CatalogoException.conflicto("idEspecialidad", "La especialidad no está vinculada al profesional.");
		}
		Boolean compatible = consultas.queryForObject("""
				SELECT (r.is_general_room OR EXISTS (SELECT 1 FROM room_specialty rs
				WHERE rs.id_room = r.id_room AND rs.id_specialty = ?)) AND (u.id_specialty IS NULL OR u.id_specialty=?)
				FROM room r LEFT JOIN planning_unit u ON u.id_planning_unit=r.id_planning_unit AND u.id_institution=r.id_institution
				WHERE r.id_institution = ? AND r.id_room = ?
				""", Boolean.class, turno.especialidad(), turno.especialidad(), institucion, turno.consultorio());
		if (!Boolean.TRUE.equals(compatible)) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio no admite la especialidad del turno.");
		}
	}

	private List<EspecialidadRecurso> especialidadesProfesional(UUID profesional) {
		return consultas.query("""
				SELECT e.id_specialty, e.name_specialty FROM professional_specialty pe
				JOIN specialty e ON e.id_specialty = pe.id_specialty WHERE pe.id_professional = ?
				ORDER BY pe.is_primary_professional_specialty DESC, lower(e.name_specialty), e.id_specialty
				""", (fila, numero) -> new EspecialidadRecurso(fila.getObject(1, UUID.class), fila.getString(2)), profesional);
	}

	private List<EspecialidadRecurso> especialidadesConsultorio(UUID consultorio) {
		return consultas.query("""
				SELECT e.id_specialty, e.name_specialty FROM room_specialty re
				JOIN specialty e ON e.id_specialty = re.id_specialty WHERE re.id_room = ?
				ORDER BY lower(e.name_specialty), e.id_specialty
				""", (fila, numero) -> new EspecialidadRecurso(fila.getObject(1, UUID.class), fila.getString(2)), consultorio);
	}

	private List<EspecialidadRecurso> especialidadesPermitidas(UUID consultorio, boolean general, UUID area, String nombreArea) {
		if (area == null) { return especialidadesConsultorio(consultorio); }
		if (general) { return List.of(new EspecialidadRecurso(area, nombreArea)); }
		return especialidadesConsultorio(consultorio).stream().filter(especialidad -> especialidad.id().equals(area)).toList();
	}

	private String estado(String estado) { return "active".equals(estado) ? "Activo" : "Inactivo"; }

}
