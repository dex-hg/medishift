package com.dextre.medishift.disponibilidad;

import java.math.BigInteger;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.SolicitudDisponibilidad;

public final class ValidadorDisponibilidades {

	private static final Set<String> CAMPOS = Set.of("idProfesional", "diaSemana", "horaInicio", "horaFin",
			"fechaInicio", "fechaFin", "estado", "observacion");

	private ValidadorDisponibilidades() {
	}

	public static SolicitudDisponibilidad validar(Map<String, Object> solicitud) {
		if (solicitud == null) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Envía un objeto JSON con los datos.", Map.of());
		}
		Map<String, String> errores = new LinkedHashMap<>();
		for (String campo : solicitud.keySet()) {
			if (!CAMPOS.contains(campo)) {
				errores.put(campo, "Este campo no forma parte del formulario.");
			}
		}
		UUID profesional = validarProfesional(solicitud.get("idProfesional"), errores);
		int dia = validarDia(solicitud.get("diaSemana"), errores);
		LocalTime horaInicio = validarHora(solicitud.get("horaInicio"), "horaInicio", errores);
		LocalTime horaFin = validarHora(solicitud.get("horaFin"), "horaFin", errores);
		LocalDate fechaInicio = validarFecha(solicitud.get("fechaInicio"), "fechaInicio", errores);
		LocalDate fechaFin = validarFecha(solicitud.get("fechaFin"), "fechaFin", errores);
		String estado = validarTexto(solicitud.get("estado"), "estado", 16, errores);
		String observacion = solicitud.containsKey("observacion")
				? validarTexto(solicitud.get("observacion"), "observacion", 240, errores) : "";
		if (!Set.of("Activo", "Inactivo").contains(estado)) {
			errores.putIfAbsent("estado", "Selecciona Activo o Inactivo.");
		}
		if (horaInicio != null && horaFin != null && !horaFin.isAfter(horaInicio)) {
			errores.put("horaFin", "La hora final debe ser posterior al inicio dentro del mismo día.");
		}
		if (fechaInicio != null && fechaFin != null) {
			if (fechaFin.isBefore(fechaInicio)) {
				errores.put("fechaFin", "El fin de vigencia debe ser igual o posterior a su inicio.");
			} else if (dia > 0 && !tieneOcurrencia(fechaInicio, fechaFin, dia)) {
				errores.put("fechaFin", "La vigencia debe contener al menos una fecha del día semanal elegido.");
			}
		}
		if (!errores.isEmpty()) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos de la disponibilidad.", errores);
		}
		return new SolicitudDisponibilidad(profesional, dia, horaInicio, horaFin, fechaInicio, fechaFin, estado, observacion);
	}

	public static boolean tieneOcurrencia(LocalDate inicio, LocalDate fin, int diaSemana) {
		if (inicio.isAfter(fin) || diaSemana < 1 || diaSemana > 7) {
			return false;
		}
		int diasHastaPrimera = Math.floorMod(diaSemana - inicio.getDayOfWeek().getValue(), 7);
		return fin.toEpochDay() - inicio.toEpochDay() >= diasHastaPrimera;
	}

	private static UUID validarProfesional(Object valor, Map<String, String> errores) {
		if (valor instanceof String texto) {
			try {
				return ValidadorCatalogos.validarId(texto);
			} catch (CatalogoException excepcion) {
				// El error pertenece al selector, no al identificador de la ruta HTTP.
			}
		}
		errores.put("idProfesional", "Selecciona un profesional mediante un UUID completo.");
		return null;
	}

	private static int validarDia(Object valor, Map<String, String> errores) {
		if (valor instanceof Integer || valor instanceof Long || valor instanceof BigInteger) {
			BigInteger numero = new BigInteger(valor.toString());
			if (numero.compareTo(BigInteger.ONE) >= 0 && numero.compareTo(BigInteger.valueOf(7)) <= 0) {
				return numero.intValue();
			}
		}
		errores.put("diaSemana", "Selecciona un día entero del 1 (lunes) al 7 (domingo).");
		return 0;
	}

	private static LocalTime validarHora(Object valor, String campo, Map<String, String> errores) {
		if (valor instanceof String texto && texto.matches("[0-9]{2}:[0-9]{2}")) {
			try {
				return LocalTime.parse(texto);
			} catch (DateTimeException excepcion) {
				// LocalTime rechaza horas fuera del calendario, como 24:00 y 08:60.
			}
		}
		errores.put(campo, "Ingresa una hora válida con formato HH:mm.");
		return null;
	}

	private static LocalDate validarFecha(Object valor, String campo, Map<String, String> errores) {
		if (valor instanceof String texto && texto.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			try {
				LocalDate fecha = LocalDate.parse(texto);
				if (fecha.getYear() > 0) {
					return fecha;
				}
			} catch (DateTimeException excepcion) {
				// No se corrigen automáticamente fechas inexistentes, como el 30 de febrero.
			}
		}
		errores.put(campo, "Ingresa una fecha válida con formato YYYY-MM-DD.");
		return null;
	}

	private static String validarTexto(Object valor, String campo, int limite, Map<String, String> errores) {
		if (!(valor instanceof String contenido)) {
			errores.put(campo, "Este campo debe contener texto.");
			return "";
		}
		for (int indice = 0; indice < contenido.length(); indice++) {
			char caracter = contenido.charAt(indice);
			if (Character.isHighSurrogate(caracter)) {
				if (indice + 1 >= contenido.length() || !Character.isLowSurrogate(contenido.charAt(indice + 1))) {
					errores.put(campo, "El texto contiene una secuencia Unicode no válida.");
					break;
				}
				indice++;
			} else if (Character.isLowSurrogate(caracter) || Character.isISOControl(caracter)) {
				errores.put(campo, "El texto contiene un carácter no permitido.");
				break;
			}
		}
		String texto = Normalizer.normalize(contenido, Normalizer.Form.NFC)
				.replaceAll("[\\p{Z}\\s]+", " ").trim();
		if (texto.codePointCount(0, texto.length()) > limite) {
			errores.putIfAbsent(campo, "Este campo admite hasta " + limite + " caracteres.");
		}
		return texto;
	}

}
