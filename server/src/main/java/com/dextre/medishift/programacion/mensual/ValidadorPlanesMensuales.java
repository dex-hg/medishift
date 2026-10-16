package com.dextre.medishift.programacion.mensual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudPlan;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudUnidad;

/** Entradas estrictas: los campos institucionales, de estado y autor se derivan en el servidor. */
public final class ValidadorPlanesMensuales {

	private ValidadorPlanesMensuales() { }

	public static SolicitudUnidad unidad(Map<String, Object> datos) {
		Map<String, String> errores = iniciar(datos, Set.of("codigo", "nombre", "tipo"));
		String codigo = texto(datos, "codigo", 32, errores);
		String nombre = texto(datos, "nombre", 140, errores);
		String tipo = texto(datos, "tipo", 16, errores);
		if (!Set.of("Departamento", "Servicio").contains(tipo)) {
			errores.put("tipo", "Selecciona Departamento o Servicio.");
		}
		comprobar(errores);
		return new SolicitudUnidad(codigo, nombre, tipo);
	}

	public static SolicitudPlan plan(Map<String, Object> datos) {
		Map<String, String> errores = iniciar(datos, Set.of("idUnidad", "periodo"));
		UUID unidad = null;
		PeriodoMensual mes = null;
		try { unidad = identificador(datos.get("idUnidad"), "idUnidad"); }
		catch (CatalogoException fallo) { errores.putAll(fallo.obtenerErrores()); }
		try { mes = periodo(datos.get("periodo")); }
		catch (CatalogoException fallo) { errores.putAll(fallo.obtenerErrores()); }
		comprobar(errores);
		return new SolicitudPlan(unidad, mes);
	}

	public static UUID identificador(Object valor, String campo) {
		if (valor instanceof String contenido) {
			try { return ValidadorCatalogos.validarId(contenido); }
			catch (CatalogoException fallo) { /* Se conserva el nombre del campo mensual. */ }
		}
		throw invalido(Map.of(campo, "Envía un identificador UUID completo."));
	}

	public static PeriodoMensual periodo(Object valor) {
		if (valor instanceof String contenido) {
			try { return PeriodoMensual.analizar(contenido); }
			catch (IllegalArgumentException fallo) { /* Se traduce al contrato de errores HTTP. */ }
		}
		throw invalido(Map.of("periodo", "Usa un mes válido con formato AAAA-MM, entre 0001 y 9999."));
	}

	private static Map<String, String> iniciar(Map<String, Object> datos, Set<String> permitidos) {
		if (datos == null) { throw invalido(Map.of()); }
		Map<String, String> errores = new LinkedHashMap<>();
		for (String campo : datos.keySet()) {
			if (!permitidos.contains(campo)) { errores.put(campo, "Este campo no forma parte del formulario."); }
		}
		return errores;
	}

	private static String texto(Map<String, Object> datos, String campo, int limite, Map<String, String> errores) {
		if (!(datos.get(campo) instanceof String contenido)) {
			errores.put(campo, "Completa este campo con texto.");
			return "";
		}
		for (int indice = 0; indice < contenido.length(); indice++) {
			char caracter = contenido.charAt(indice);
			if (Character.isHighSurrogate(caracter)) {
				if (indice + 1 >= contenido.length() || !Character.isLowSurrogate(contenido.charAt(indice + 1))) {
					errores.put(campo, "El texto contiene una secuencia Unicode inválida.");
					break;
				}
				indice++;
			} else if (Character.isLowSurrogate(caracter) || Character.isISOControl(caracter)) {
				errores.put(campo, "El texto contiene un carácter no permitido.");
				break;
			}
		}
		String normalizado = ValidadorCatalogos.normalizarTexto(contenido);
		if (normalizado.isEmpty() || normalizado.codePointCount(0, normalizado.length()) > limite) {
			errores.putIfAbsent(campo, "Ingresa entre 1 y " + limite + " caracteres.");
		}
		return normalizado;
	}

	private static void comprobar(Map<String, String> errores) {
		if (!errores.isEmpty()) { throw invalido(errores); }
	}

	private static CatalogoException invalido(Map<String, String> errores) {
		return new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos del formulario.", errores);
	}
}
