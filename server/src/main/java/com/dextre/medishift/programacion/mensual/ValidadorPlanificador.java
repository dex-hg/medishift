package com.dextre.medishift.programacion.mensual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.ValidadorProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudAsignacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudConsultorio;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudArea;

public final class ValidadorPlanificador {

	private ValidadorPlanificador() { }

	public static SolicitudArea areaNueva(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("codigo", "nombre", "idEspecialidad"));
		UUID especialidad = ValidadorPlanesMensuales.identificador(datos.get("idEspecialidad"), "idEspecialidad");
		Map<String, String> errores = new LinkedHashMap<>();
		String codigo = texto(datos, "codigo", 32, true, errores);
		String nombre = texto(datos, "nombre", 140, true, errores);
		if (!errores.isEmpty()) { throw invalido(errores); }
		return new SolicitudArea(codigo, nombre, especialidad);
	}

	public static UUID especialidadArea(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("idEspecialidad"));
		return ValidadorPlanesMensuales.identificador(datos.get("idEspecialidad"), "idEspecialidad");
	}

	public static SolicitudAsignacion turno(Map<String, Object> datos) {
		if (datos == null) { throw invalido(Map.of()); }
		UUID area = ValidadorPlanesMensuales.identificador(datos.get("idArea"), "idArea");
		Map<String, Object> turno = new LinkedHashMap<>(datos);
		turno.remove("idArea");
		return new SolicitudAsignacion(area, ValidadorProgramacion.validarTurno(turno));
	}

	public static UUID area(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("idArea"));
		return ValidadorPlanesMensuales.identificador(datos.get("idArea"), "idArea");
	}

	public static SolicitudConsultorio consultorio(Map<String, Object> datos) {
		comprobarCampos(datos, Set.of("idArea", "codigo", "nombre", "ubicacion"));
		UUID area = ValidadorPlanesMensuales.identificador(datos.get("idArea"), "idArea");
		Map<String, String> errores = new LinkedHashMap<>();
		String codigo = texto(datos, "codigo", 20, true, errores);
		String nombre = texto(datos, "nombre", 100, true, errores);
		String ubicacion = texto(datos, "ubicacion", 100, true, errores);
		if (!errores.isEmpty()) { throw invalido(errores); }
		return new SolicitudConsultorio(area, codigo, nombre, ubicacion);
	}

	private static void comprobarCampos(Map<String, Object> datos, Set<String> permitidos) {
		if (datos == null) { throw invalido(Map.of()); }
		Map<String, String> errores = new LinkedHashMap<>();
		for (String campo : datos.keySet()) {
			if (!permitidos.contains(campo)) { errores.put(campo, "Este campo no forma parte del formulario."); }
		}
		if (!errores.isEmpty()) { throw invalido(errores); }
	}

	private static String texto(Map<String, Object> datos, String campo, int limite, boolean obligatorio,
			Map<String, String> errores) {
		if (!datos.containsKey(campo) && !obligatorio) { return ""; }
		if (!(datos.get(campo) instanceof String contenido)) {
			errores.put(campo, "Completa este campo con texto.");
			return "";
		}
		for (int indice = 0; indice < contenido.length(); indice++) {
			char caracter = contenido.charAt(indice);
			if (Character.isHighSurrogate(caracter) && indice + 1 < contenido.length()
					&& Character.isLowSurrogate(contenido.charAt(indice + 1))) { indice++; }
			else if (Character.isSurrogate(caracter) || Character.isISOControl(caracter)) {
				errores.put(campo, "El texto contiene un carácter no permitido.");
			}
		}
		String normalizado = ValidadorCatalogos.normalizarTexto(contenido);
		if ((obligatorio && normalizado.isEmpty()) || normalizado.codePointCount(0, normalizado.length()) > limite) {
			errores.put(campo, "Ingresa " + (obligatorio ? "entre 1 y " : "hasta ") + limite + " caracteres.");
		}
		return normalizado;
	}

	private static CatalogoException invalido(Map<String, String> errores) {
		return new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos del formulario.", errores);
	}
}
