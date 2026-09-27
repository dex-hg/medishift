package com.dextre.medishift.registro;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidadorRegistro {

	private static final Set<String> ZONAS_HORARIAS = Set.of("America/Lima", "America/Bogota",
			"America/Santiago", "America/La_Paz", "America/Mexico_City",
			"America/Argentina/Buenos_Aires");
	private static final Pattern FORMATO_CORREO = Pattern.compile(
			"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]"
					+ "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
					+ "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

	private ValidadorRegistro() {
	}

	public static DatosRegistro validar(SolicitudRegistro solicitud) {
		Map<String, String> errores = new LinkedHashMap<>();
		if (solicitud == null) {
			throw new ValidacionRegistroException(Map.of("registro", "Envía un objeto JSON de registro."));
		}

		String codigo = leerTexto(solicitud.codigoInstitucion(), "codigoInstitucion", errores);
		String nombre = leerTexto(solicitud.nombreInstitucion(), "nombreInstitucion", errores);
		String zona = leerTexto(solicitud.zonaHoraria(), "zonaHoraria", errores);
		String correo = leerTexto(solicitud.correo(), "correo", errores);
		String contrasena = leerTexto(solicitud.contrasena(), "contrasena", errores);

		codigo = recortarEspacios(codigo);
		nombre = recortarEspacios(nombre);
		correo = recortarEspacios(correo).toLowerCase(Locale.ROOT);
		validarTexto(codigo, "codigoInstitucion", 32, errores);
		validarTexto(nombre, "nombreInstitucion", 140, errores);
		validarTexto(zona, "zonaHoraria", 64, errores);
		validarTexto(correo, "correo", 254, errores);
		validarTexto(contrasena, "contrasena", 1024, errores);

		if (!errores.containsKey("zonaHoraria") && !ZONAS_HORARIAS.contains(zona)) {
			errores.put("zonaHoraria", "Selecciona una zona horaria de la lista.");
		}
		if (!errores.containsKey("correo") && !FORMATO_CORREO.matcher(correo).matches()) {
			errores.put("correo", "Ingresa un correo válido, por ejemplo nombre@institucion.pe.");
		}
		if (!errores.isEmpty()) {
			throw new ValidacionRegistroException(errores);
		}
		return new DatosRegistro(codigo, nombre, zona, correo, contrasena);
	}

	private static String leerTexto(Object valor, String campo, Map<String, String> errores) {
		if (!(valor instanceof String texto)) {
			errores.put(campo, "Este campo debe contener texto.");
			return "";
		}
		for (int indice = 0; indice < texto.length(); indice++) {
			char caracter = texto.charAt(indice);
			if (caracter == '\0') {
				errores.put(campo, "El texto contiene un carácter no permitido.");
				break;
			}
			if (Character.isHighSurrogate(caracter)) {
				if (indice + 1 >= texto.length() || !Character.isLowSurrogate(texto.charAt(indice + 1))) {
					errores.put(campo, "El texto contiene una secuencia Unicode no válida.");
					break;
				}
				indice++;
			} else if (Character.isLowSurrogate(caracter)) {
				errores.put(campo, "El texto contiene una secuencia Unicode no válida.");
				break;
			}
		}
		return texto;
	}

	private static void validarTexto(String texto, String campo, int limite, Map<String, String> errores) {
		if (errores.containsKey(campo)) {
			return;
		}
		if (recortarEspacios(texto).isEmpty()) {
			errores.put(campo, "Completa este campo; no puede contener solo espacios.");
		} else if (texto.codePointCount(0, texto.length()) > limite) {
			errores.put(campo, "Este campo admite hasta " + limite + " caracteres.");
		}
	}

	/** Replica String.trim() de JavaScript, incluido NBSP y BOM, sin alterar la contraseña. */
	private static String recortarEspacios(String texto) {
		int inicio = 0;
		int fin = texto.length();
		while (inicio < fin && esEspacioJavascript(texto.charAt(inicio))) {
			inicio++;
		}
		while (fin > inicio && esEspacioJavascript(texto.charAt(fin - 1))) {
			fin--;
		}
		return texto.substring(inicio, fin);
	}

	private static boolean esEspacioJavascript(char caracter) {
		return (caracter >= '\u0009' && caracter <= '\r') || caracter == '\u0020'
				|| caracter == '\u00a0' || caracter == '\u1680'
				|| (caracter >= '\u2000' && caracter <= '\u200a')
				|| caracter == '\u2028' || caracter == '\u2029' || caracter == '\u202f'
				|| caracter == '\u205f' || caracter == '\u3000' || caracter == '\ufeff';
	}

}
