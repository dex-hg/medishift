package com.dextre.medishift.programacion;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;

/** Fechas locales de la institución; nunca depende de la zona del navegador o servidor. */
public final class TiempoProgramacion {

	public record Ventana(int diaSemana, LocalTime inicio, LocalTime fin, LocalDate desde, LocalDate hasta) { }

	private TiempoProgramacion() { }

	public static LocalDate analizarFecha(String valor) {
		try {
			if (valor != null && valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
				LocalDate fecha = LocalDate.parse(valor);
				if (fecha.getYear() >= 1) { return fecha; }
			}
		} catch (DateTimeException excepcion) {
			// Las fechas imposibles tampoco forman parte del contrato.
		}
		throw invalido("fecha", "Ingresa una fecha válida con formato YYYY-MM-DD.");
	}

	public static LocalTime analizarHora(String valor) {
		try {
			if (valor != null && valor.matches("[0-9]{2}:[0-9]{2}")) {
				return LocalTime.parse(valor, DateTimeFormatter.ISO_LOCAL_TIME);
			}
		} catch (DateTimeException excepcion) {
			// Solo se aceptan minutos reales: 24:00 requiere un modelo diferente.
		}
		throw invalido("hora", "Ingresa una hora válida con formato HH:mm.");
	}

	public static Instant resolver(LocalDate fecha, LocalTime hora, ZoneId zona) {
		LocalDateTime local = LocalDateTime.of(fecha, hora);
		List<ZoneOffset> desplazamientos = zona.getRules().getValidOffsets(local);
		if (desplazamientos.size() != 1) {
			String detalle = desplazamientos.isEmpty() ? "no existe" : "se repite";
			throw invalido("hora", "La hora " + local + " " + detalle + " en " + zona
					+ " por el cambio de hora. Selecciona otra hora.");
		}
		return local.toInstant(desplazamientos.getFirst());
	}

	public static LocalDate inicioSemana(LocalDate fecha) {
		return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	public static boolean cubrirDisponibilidad(List<Ventana> ventanas, LocalDate fecha,
			LocalTime inicio, LocalTime fin) {
		if (!fin.isAfter(inicio)) { return false; }
		LocalTime cubierto = inicio;
		List<Ventana> vigentes = ventanas.stream()
				.filter(ventana -> ventana.diaSemana() == fecha.getDayOfWeek().getValue()
						&& !fecha.isBefore(ventana.desde()) && !fecha.isAfter(ventana.hasta()))
				.sorted(Comparator.comparing(Ventana::inicio)).toList();
		for (Ventana ventana : vigentes) {
			if (ventana.inicio().isAfter(cubierto)) { return false; }
			if (ventana.fin().isAfter(cubierto)) { cubierto = ventana.fin(); }
			if (!cubierto.isBefore(fin)) { return true; }
		}
		return false;
	}

	public static boolean seSuperponen(Instant inicio, Instant fin, Instant otroInicio, Instant otroFin) {
		return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
	}

	public static long segundosDentro(Instant inicio, Instant fin, Instant desde, Instant hasta) {
		return duracionDentro(inicio, fin, desde, hasta).getSeconds();
	}

	public static Duration duracionDentro(Instant inicio, Instant fin, Instant desde, Instant hasta) {
		Instant interseccionInicio = inicio.isAfter(desde) ? inicio : desde;
		Instant interseccionFin = fin.isBefore(hasta) ? fin : hasta;
		return interseccionFin.isAfter(interseccionInicio)
				? Duration.between(interseccionInicio, interseccionFin) : Duration.ZERO;
	}

	private static CatalogoException invalido(String campo, String mensaje) {
		return new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa las fechas y horas.", Map.of(campo, mensaje));
	}

}
