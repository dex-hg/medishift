package com.dextre.medishift.programacion.mensual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dextre.medishift.catalogos.DatosCatalogos.Etiqueta;
import com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.ProfesionalRecurso;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudTurno;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioFeriados;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Un único calendario mensual; las áreas agrupan sus consultorios. */
public final class DatosPlanificador {

	private DatosPlanificador() { }

	public record Area(UUID id, String codigo, String nombre, String estado, UUID idAreaPadre,
			List<EspecialidadRecurso> etiquetas) {
		@JsonProperty("idEspecialidad") public UUID idEspecialidad() {
			return etiquetas.size() == 1 ? etiquetas.getFirst().id() : null;
		}
		@JsonProperty("especialidad") public String especialidad() {
			return etiquetas.size() == 1 ? etiquetas.getFirst().nombre() : "";
		}
		@JsonProperty("idsEtiquetas") public List<UUID> idsEtiquetas() { return etiquetas.stream().map(EspecialidadRecurso::id).toList(); }
		@JsonProperty("revision") public String revision() { return RevisionesProgramacion.resumir(this); }
	}

	public record ConsultorioArea(UUID id, String codigo, String nombre, UUID idArea, UUID idEspecialidadArea, String estado,
			boolean usoGeneral, List<EspecialidadRecurso> especialidades) {
		@JsonProperty("etiquetas") public List<EspecialidadRecurso> etiquetas() { return especialidades; }
	}

	public record SolicitudArea(String codigo, String nombre, UUID idAreaPadre, List<UUID> idsEtiquetas, String estado) { }

	public record CalendarioMensual(String zonaHoraria, List<Area> areas, List<ConsultorioArea> consultorios,
			List<ProfesionalRecurso> profesionales, List<PlanMensual> planes, List<TurnoMensual> turnos,
			List<Etiqueta> etiquetas, CalendarioFeriados feriados,
			com.dextre.medishift.cuenta.DatosConfiguracion.Politica politica,
			com.dextre.medishift.acceso.ServicioAccesoMensual.PermisosProgramacion permisos,
			List<OcupacionConsultorio> ocupaciones) {
		@JsonProperty("especialidades") public List<EspecialidadRecurso> especialidades() {
			return etiquetas.stream().map(etiqueta -> new EspecialidadRecurso(etiqueta.id(), etiqueta.nombre())).toList();
		}
	}

	/** Reservas compartidas sin nombres, notas ni datos de asistencia de otros profesionales. */
	public record OcupacionConsultorio(UUID idTurno, UUID idConsultorio, java.time.Instant inicio, java.time.Instant fin) { }

	public record TurnoMensual(UUID id, UUID idPlan, UUID idArea, UUID idProfesional, String profesional,
			UUID idConsultorio, String consultorio, UUID idEspecialidad, String especialidad, LocalDate fecha,
			String horaInicio, String horaFin, String estado, String observacion, String revision,
			String asistencia, String revisionAsistencia) {
		@JsonProperty("idEtiqueta") public UUID idEtiqueta() { return idEspecialidad; }
		@JsonProperty("etiqueta") public String etiqueta() { return especialidad; }
	}

	public record AsistenciaTurno(UUID idTurno, String asistencia, String revisionAsistencia) { }

	public record SolicitudAsignacion(UUID idArea, SolicitudTurno turno) { }

	public record SolicitudConsultorio(UUID idArea, String codigo, String nombre, String ubicacion, List<UUID> idsEtiquetas) { }

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
