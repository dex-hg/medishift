package com.dextre.medishift.programacion.mensual;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Intervalo positivo de una asignación, con fin exclusivo y duración real. */
public record IntervaloAsignacion(Instant inicio, Instant fin) {

	public IntervaloAsignacion {
		exigir(inicio, "El instante de inicio es obligatorio.");
		exigir(fin, "El instante de fin es obligatorio.");
		if (!fin.isAfter(inicio)) {
			throw new IllegalArgumentException("El fin de la asignación debe ser posterior al inicio.");
		}
	}

	/**
	 * Convierte los dos extremos locales usando la zona institucional.
	 * La fecha final es obligatoria; una hora final igual o anterior no implica otro día.
	 */
	public static IntervaloAsignacion desde(LocalDate fechaInicio, LocalTime horaInicio,
			LocalDate fechaFin, LocalTime horaFin, ZoneId zona) {
		exigir(fechaInicio, "La fecha de inicio es obligatoria.");
		exigir(horaInicio, "La hora de inicio es obligatoria.");
		exigir(fechaFin, "La fecha de fin explícita es obligatoria.");
		exigir(horaFin, "La hora de fin es obligatoria.");
		exigir(zona, "La zona horaria institucional es obligatoria.");
		Instant inicio = resolver(fechaInicio, horaInicio, zona);
		Instant fin = resolver(fechaFin, horaFin, zona);
		return new IntervaloAsignacion(inicio, fin);
	}

	public Duration duracion() {
		return Duration.between(inicio, fin);
	}

	/** Parte del intervalo que pertenece a un día local de la institución. */
	public Duration duracionEnFecha(LocalDate fecha, ZoneId zona) {
		exigir(fecha, "La fecha para calcular la duración es obligatoria.");
		exigir(zona, "La zona horaria institucional es obligatoria.");
		Instant desde = fecha.atStartOfDay(zona).toInstant();
		Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();
		return duracionDentro(desde, hasta);
	}

	/** Parte del intervalo que pertenece al mes completo, según la zona institucional. */
	public Duration duracionEnPeriodo(PeriodoMensual periodo, ZoneId zona) {
		exigir(periodo, "El periodo mensual para calcular la duración es obligatorio.");
		exigir(zona, "La zona horaria institucional es obligatoria.");
		return duracionDentro(periodo.inicioInstantaneo(zona), periodo.finExclusivoInstantaneo(zona));
	}

	private Duration duracionDentro(Instant desde, Instant hasta) {
		Instant inicioInterseccion = inicio.isAfter(desde) ? inicio : desde;
		Instant finInterseccion = fin.isBefore(hasta) ? fin : hasta;
		return finInterseccion.isAfter(inicioInterseccion)
				? Duration.between(inicioInterseccion, finInterseccion) : Duration.ZERO;
	}

	private static Instant resolver(LocalDate fecha, LocalTime hora, ZoneId zona) {
		LocalDateTime fechaHora = LocalDateTime.of(fecha, hora);
		List<ZoneOffset> desplazamientos = zona.getRules().getValidOffsets(fechaHora);
		if (desplazamientos.size() != 1) {
			String detalle = desplazamientos.isEmpty() ? "no existe" : "se repite";
			throw new IllegalArgumentException("La hora " + fechaHora + " " + detalle + " en " + zona
					+ " por el cambio de hora. Selecciona otra hora.");
		}
		return fechaHora.toInstant(desplazamientos.getFirst());
	}

	private static void exigir(Object valor, String mensaje) {
		if (valor == null) {
			throw new IllegalArgumentException(mensaje);
		}
	}
}
