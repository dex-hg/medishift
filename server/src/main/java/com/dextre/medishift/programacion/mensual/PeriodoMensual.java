package com.dextre.medishift.programacion.mensual;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Mes calendario completo de una institución, independiente de la zona del servidor. */
public record PeriodoMensual(int anio, int mes) {

	public PeriodoMensual {
		if (anio < 1 || anio > 9999) {
			throw new IllegalArgumentException("El año del periodo debe estar entre 1 y 9999.");
		}
		if (mes < 1 || mes > 12) {
			throw new IllegalArgumentException("El mes del periodo debe estar entre 1 y 12.");
		}
	}

	/** Acepta exactamente YYYY-MM, sin espacios, signos ni componentes de fecha adicionales. */
	public static PeriodoMensual analizar(String valor) {
		if (valor == null || !valor.matches("[0-9]{4}-[0-9]{2}")) {
			throw new IllegalArgumentException("Ingresa un periodo válido con formato YYYY-MM.");
		}
		int anio = Integer.parseInt(valor.substring(0, 4));
		int mes = Integer.parseInt(valor.substring(5, 7));
		return new PeriodoMensual(anio, mes);
	}

	public LocalDate inicio() {
		return mesCalendario().atDay(1);
	}

	/** Último día del mes, incluido dentro del periodo. */
	public LocalDate fin() {
		return mesCalendario().atEndOfMonth();
	}

	/**
	 * Primer día fuera del periodo. Para diciembre de 9999 devuelve 10000-01-01:
	 * es un límite interno válido de java.time, aunque no sea un periodo admitido.
	 */
	public LocalDate finExclusivo() {
		return fin().plusDays(1);
	}

	public int cantidadDias() {
		return mesCalendario().lengthOfMonth();
	}

	/** Una fecha nula no pertenece al periodo. Los dos extremos locales se incluyen. */
	public boolean contiene(LocalDate fecha) {
		return fecha != null && !fecha.isBefore(inicio()) && !fecha.isAfter(fin());
	}

	/**
	 * Lunes de todas las semanas que intersectan el mes, en orden y sin duplicados.
	 * El primer lunes puede corresponder al mes o año anterior.
	 */
	public List<LocalDate> iniciosSemanas() {
		List<LocalDate> inicios = new ArrayList<>();
		LocalDate lunes = inicio().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate ultimoDia = fin();
		while (!lunes.isAfter(ultimoDia)) {
			inicios.add(lunes);
			lunes = lunes.plusWeeks(1);
		}
		return List.copyOf(inicios);
	}

	/** Primer instante válido del primer día, según las reglas de la zona institucional. */
	public Instant inicioInstantaneo(ZoneId zona) {
		return inicio().atStartOfDay(validarZona(zona)).toInstant();
	}

	/**
	 * Primer instante válido del día siguiente al mes. Se resuelve por fecha local;
	 * su distancia al inicio puede variar con los cambios de hora de la institución.
	 */
	public Instant finExclusivoInstantaneo(ZoneId zona) {
		return finExclusivo().atStartOfDay(validarZona(zona)).toInstant();
	}

	private YearMonth mesCalendario() {
		return YearMonth.of(anio, mes);
	}

	private static ZoneId validarZona(ZoneId zona) {
		if (zona == null) {
			throw new IllegalArgumentException("La zona horaria institucional es obligatoria.");
		}
		return zona;
	}
}
