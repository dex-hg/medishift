package com.dextre.medishift.programacion.mensual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.ProfesionalRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudTurno;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioFeriados;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;

/** Un único calendario mensual; las áreas agrupan sus consultorios. */
public final class DatosPlanificador {

	private DatosPlanificador() { }

	public record Area(UUID id, String codigo, String nombre, String estado, UUID idEspecialidad, String especialidad) { }

	public record ConsultorioArea(UUID id, String codigo, String nombre, UUID idArea, UUID idEspecialidadArea, String estado,
			boolean usoGeneral, List<EspecialidadRecurso> especialidades) { }

	public record SolicitudArea(String codigo, String nombre, UUID idEspecialidad) { }

	public record CalendarioMensual(String zonaHoraria, List<Area> areas, List<ConsultorioArea> consultorios,
			List<ProfesionalRecurso> profesionales, List<PlanMensual> planes, List<TurnoMensual> turnos,
			List<EspecialidadRecurso> especialidades, CalendarioFeriados feriados) { }

	public record TurnoMensual(UUID id, UUID idPlan, UUID idArea, UUID idProfesional, String profesional,
			UUID idConsultorio, String consultorio, UUID idEspecialidad, String especialidad, LocalDate fecha,
			String horaInicio, String horaFin, String estado, String observacion, String revision,
			String asistencia, String revisionAsistencia) { }

	public record AsistenciaTurno(UUID idTurno, String asistencia, String revisionAsistencia) { }

	public record SolicitudAsignacion(UUID idArea, SolicitudTurno turno) { }

	public record SolicitudConsultorio(UUID idArea, String codigo, String nombre, String ubicacion) { }

	public record RegistroTurno(Turno turno, UUID idArea, String estadoPlan, UUID idAsistencia,
			String estadoAsistencia, String asistenciaRegistrada) {
		public String asistencia() {
			return "absent".equals(estadoAsistencia) ? "Falta" : "Asistencia";
		}

		public String revisionAsistencia() {
			return RevisionesProgramacion.resumir(RevisionesProgramacion.deTurno(turno), idArea, estadoPlan, asistenciaRegistrada);
		}
	}
}
