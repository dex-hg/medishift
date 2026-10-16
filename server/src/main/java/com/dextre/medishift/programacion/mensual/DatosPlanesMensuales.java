package com.dextre.medishift.programacion.mensual;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DatosPlanesMensuales {

	private DatosPlanesMensuales() { }

	public record UnidadPlanificacion(UUID id, String codigo, String nombre, String tipo, String estado) { }

	public record ListadoUnidades(String zonaHoraria, List<UnidadPlanificacion> unidades) { }

	public record SolicitudUnidad(String codigo, String nombre, String tipo) { }

	public record SolicitudPlan(UUID idUnidad, PeriodoMensual periodo) { }

	public record PlanMensual(UUID id, UUID idUnidad, String periodo, LocalDate fechaInicio, LocalDate fechaFin,
			String zonaHoraria, int version, String estado, UUID idCreador, Instant creadoEn,
			UUID idAprobador, Instant aprobadoEn, String revision) { }

	public record ListadoPlanes(List<PlanMensual> planes) { }
}
