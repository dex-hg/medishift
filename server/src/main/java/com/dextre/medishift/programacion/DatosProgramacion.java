package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class DatosProgramacion {

	private DatosProgramacion() { }

	public record SolicitudTurno(UUID idProfesional, UUID idConsultorio, UUID idEspecialidad,
			LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String observacion) { }

	public record SolicitudSemana(LocalDate fechaInicio, UUID idHorario, String revision) { }

	public record Horario(UUID id, UUID institucion, LocalDate inicio, LocalDate fin,
			int version, String estado, UUID creador, UUID aprobador, Instant creado, Instant aprobado) { }

	public record Turno(UUID id, UUID horario, UUID profesional, UUID consultorio, UUID especialidad,
			Instant inicio, Instant fin, String estado, String observacion,
			String nombreProfesional, String nombreConsultorio, String nombreEspecialidad) { }

	public record TurnoVista(UUID id, UUID idProfesional, String profesional, UUID idConsultorio,
			String consultorio, UUID idEspecialidad, String especialidad, LocalDate fecha,
			String horaInicio, String horaFin, String estado, String observacion, String revision,
			String zonaHoraria) { }

	public record SemanaVista(LocalDate fechaInicio, LocalDate fechaFin, String zonaHoraria,
			UUID idHorario, int version, String estado, String revision, List<TurnoVista> turnos,
			Integer versionAprobada) { }

	public record EspecialidadRecurso(UUID id, String nombre) { }

	public record ProfesionalRecurso(UUID id, String nombre, String estado,
			List<EspecialidadRecurso> especialidades) { }

	public record ConsultorioRecurso(UUID id, String codigo, String nombre, String estado,
			boolean usoGeneral, List<EspecialidadRecurso> especialidades) { }

	public record Recursos(String zonaHoraria, List<ProfesionalRecurso> profesionales,
			List<ConsultorioRecurso> consultorios) { }

	public record Requisito(UUID especialidad, Instant inicio, Instant fin, int cantidad, String observacion) {
		public Requisito(UUID especialidad, Instant inicio, Instant fin, int cantidad) {
			this(especialidad, inicio, fin, cantidad, "");
		}
	}

}
