package com.dextre.medishift.disponibilidad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class DatosDisponibilidades {

	private DatosDisponibilidades() {
	}

	public record SolicitudDisponibilidad(UUID idProfesional, int diaSemana, LocalTime horaInicio,
			LocalTime horaFin, LocalDate fechaInicio, LocalDate fechaFin, String estado, String observacion) {
	}

	public record Disponibilidad(UUID id, UUID idProfesional, String profesional, int diaSemana,
			String horaInicio, String horaFin, String fechaInicio, String fechaFin,
			String estado, String observacion, String revision) {
	}

}
