package com.dextre.medishift.cuenta;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.cuenta.DatosConfiguracion.Claves;
import com.dextre.medishift.cuenta.DatosConfiguracion.Institucion;
import com.dextre.medishift.cuenta.DatosConfiguracion.Perfil;
import com.dextre.medishift.cuenta.DatosConfiguracion.Preferencias;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

public final class ValidadorConfiguracion {

	private ValidadorConfiguracion() { }

	public static Institucion institucion(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("nombreInstitucion", "zonaHoraria", "minutosDiarios", "minutosSemanales", "minutosPorFeriado"));
		Map<String, String> errores = new LinkedHashMap<>();
		String nombre = texto(datos, "nombreInstitucion", 1, 140, errores, true);
		String zona = texto(datos, "zonaHoraria", 1, 64, errores, true);
		if (Pais.deZona(zona).isEmpty()) errores.put("zonaHoraria", "Selecciona una zona horaria de la lista.");
		int diarios = entero(datos, "minutosDiarios", 1, 1440, errores);
		int semanales = entero(datos, "minutosSemanales", 1, 10080, errores);
		int feriados = entero(datos, "minutosPorFeriado", 0, 1440, errores);
		if (!errores.containsKey("minutosDiarios") && !errores.containsKey("minutosSemanales")
				&& (semanales < diarios || semanales > diarios * 7)) {
			errores.put("minutosSemanales", "El máximo semanal debe estar entre uno y siete máximos diarios.");
		}
		exigirValido(errores);
		return new Institucion(nombre, zona, diarios, semanales, feriados, "");
	}

	public static Perfil perfil(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("nombre"));
		Map<String, String> errores = new LinkedHashMap<>();
		String nombre = texto(datos, "nombre", 1, 120, errores, true);
		exigirValido(errores);
		return new Perfil(nombre);
	}

	public static Preferencias preferencias(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("barraCompacta", "horaInicio", "horaFin"));
		Map<String, String> errores = new LinkedHashMap<>();
		if (!(datos.get("barraCompacta") instanceof Boolean)) errores.put("barraCompacta", "Selecciona una opción válida.");
		String inicio = hora(datos, "horaInicio", errores);
		String fin = hora(datos, "horaFin", errores);
		if (!errores.containsKey("horaInicio") && !errores.containsKey("horaFin") && inicio.compareTo(fin) >= 0) {
			errores.put("horaFin", "La hora final debe ser posterior a la inicial dentro del mismo día.");
		}
		exigirValido(errores);
		return new Preferencias(Boolean.TRUE.equals(datos.get("barraCompacta")), inicio, fin);
	}

	public static Claves claves(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("contrasenaActual", "contrasenaNueva"));
		Map<String, String> errores = new LinkedHashMap<>();
		String actual = texto(datos, "contrasenaActual", 1, 1024, errores, false);
		String nueva = texto(datos, "contrasenaNueva", 12, 128, errores, false);
		if (actual.equals(nueva)) errores.put("contrasenaNueva", "Usa una contraseña diferente a la actual.");
		exigirValido(errores);
		return new Claves(actual, nueva);
	}

	private static void comprobarCampos(Map<String, Object> datos, Set<String> permitidos) {
		if (datos == null || !datos.keySet().equals(permitidos)) {
			exigirValido(Map.of("formulario", "Envía únicamente todos los campos del formulario, sin identificadores ni permisos."));
		}
	}

	private static String texto(Map<String, Object> datos, String campo, int minimo, int maximo,
			Map<String, String> errores, boolean recortar) {
		if (!(datos.get(campo) instanceof String valor)) {
			errores.put(campo, "Ingresa texto válido.");
			return "";
		}
		String normalizado = recortar ? valor.strip() : valor;
		int longitud = normalizado.codePointCount(0, normalizado.length());
		if (normalizado.isBlank() || longitud < minimo || longitud > maximo) {
			errores.put(campo, "Ingresa entre " + minimo + " y " + maximo + " caracteres.");
		}
		if (normalizado.codePoints().anyMatch(caracter -> Character.isISOControl(caracter)
				|| (caracter >= 0xD800 && caracter <= 0xDFFF))) {
			errores.put(campo, "El texto contiene caracteres no permitidos.");
		}
		return normalizado;
	}

	private static int entero(Map<String, Object> datos, String campo, int minimo, int maximo, Map<String, String> errores) {
		if (datos.get(campo) instanceof Number numero) {
			try {
				int valor = new BigDecimal(numero.toString()).intValueExact();
				if (valor >= minimo && valor <= maximo) return valor;
			} catch (NumberFormatException | ArithmeticException excepcion) {
				// Rechaza fracciones, valores fuera de rango y números no finitos.
			}
		}
		errores.put(campo, "Ingresa minutos enteros entre " + minimo + " y " + maximo + ".");
		return 0;
	}

	private static String hora(Map<String, Object> datos, String campo, Map<String, String> errores) {
		String valor = texto(datos, campo, 5, 5, errores, false);
		try {
			if (!valor.matches("[0-2][0-9]:[0-5][0-9]")) throw new DateTimeParseException("Formato", valor, 0);
			LocalTime.parse(valor);
		} catch (DateTimeParseException excepcion) { errores.put(campo, "Ingresa una hora válida con formato HH:mm."); }
		return valor;
	}

	private static void exigirValido(Map<String, String> errores) {
		if (!errores.isEmpty()) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos del formulario.", errores);
	}
}
