package com.dextre.medishift.catalogos;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;

public final class ValidadorCatalogos {

	private static final Pattern FORMATO_CORREO = Pattern.compile(
			"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]"
					+ "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
					+ "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");
	private static final Pattern FORMATO_TELEFONO = Pattern.compile("^\\+?[0-9][0-9 ()-]*$");
	private static final Pattern ESPACIOS = Pattern.compile("[\\s\\p{Z}\\ufeff]+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Set<String> CAMPOS_PROFESIONAL = Set.of("nombres", "apellidos", "colegiatura",
			"correo", "telefono", "categoria", "estado", "especialidad");
	private static final Set<String> CAMPOS_CONSULTORIO = Set.of("codigo", "nombre", "ubicacion",
			"especialidad", "estado");

	private ValidadorCatalogos() {
	}

	public static Profesional validarProfesional(Map<String, Object> solicitud) {
		Map<String, String> errores = iniciarValidacion(solicitud, CAMPOS_PROFESIONAL);
		String nombres = texto(solicitud, "nombres", 80, true, errores);
		String apellidos = texto(solicitud, "apellidos", 100, true, errores);
		String colegiatura = texto(solicitud, "colegiatura", 32, true, errores).toUpperCase(Locale.ROOT);
		String correo = texto(solicitud, "correo", 254, true, errores).toLowerCase(Locale.ROOT);
		String telefono = texto(solicitud, "telefono", 20, false, errores);
		String categoria = texto(solicitud, "categoria", 40, true, errores);
		String estado = texto(solicitud, "estado", 16, true, errores);
		String especialidad = texto(solicitud, "especialidad", 100, true, errores);
		validarLongitud(colegiatura, "colegiatura", 32, errores);
		validarLongitud(correo, "correo", 254, errores);
		if (!errores.containsKey("correo") && !FORMATO_CORREO.matcher(correo).matches()) {
			errores.put("correo", "Ingresa un correo válido, por ejemplo nombre@institucion.pe.");
		}
		if (!telefono.isEmpty() && !errores.containsKey("telefono")) {
			long digitos = telefono.chars().filter(Character::isDigit).count();
			if (!FORMATO_TELEFONO.matcher(telefono).matches() || digitos < 7 || digitos > 15) {
				errores.put("telefono", "Ingresa un teléfono con entre 7 y 15 dígitos y prefijo opcional +.");
			}
		}
		validarEstado(estado, errores);
		lanzarErrores(errores);
		return new Profesional(null, nombres, apellidos, colegiatura, correo, telefono,
				categoria, estado, especialidad);
	}

	public static Consultorio validarConsultorio(Map<String, Object> solicitud) {
		Map<String, String> errores = iniciarValidacion(solicitud, CAMPOS_CONSULTORIO);
		String codigo = texto(solicitud, "codigo", 20, true, errores).toUpperCase(Locale.ROOT);
		String nombre = texto(solicitud, "nombre", 100, true, errores);
		String ubicacion = texto(solicitud, "ubicacion", 100, true, errores);
		String especialidad = texto(solicitud, "especialidad", 100, false, errores);
		String estado = texto(solicitud, "estado", 16, true, errores);
		validarLongitud(codigo, "codigo", 20, errores);
		validarEstado(estado, errores);
		lanzarErrores(errores);
		return new Consultorio(null, codigo, nombre, ubicacion, especialidad, estado);
	}

	public static UUID validarId(String valor) {
		try {
			UUID identificador = UUID.fromString(valor);
			if (identificador.toString().equalsIgnoreCase(valor)) {
				return identificador;
			}
		} catch (IllegalArgumentException excepcion) {
			// UUID.fromString admite ciertas abreviaturas que no forman parte del contrato.
		}
		throw new CatalogoException(HttpStatus.BAD_REQUEST, "El identificador no es válido.",
				Map.of("id", "Envía un identificador UUID completo."));
	}

	public static String normalizarTexto(String texto) {
		return ESPACIOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFC)).replaceAll(" ").trim();
	}

	private static Map<String, String> iniciarValidacion(Map<String, Object> solicitud, Set<String> campos) {
		if (solicitud == null) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Envía un objeto JSON con los datos.", Map.of());
		}
		Map<String, String> errores = new LinkedHashMap<>();
		for (String campo : solicitud.keySet()) {
			if (!campos.contains(campo)) {
				errores.put(campo, "Este campo no forma parte del formulario.");
			}
		}
		return errores;
	}

	private static String texto(Map<String, Object> solicitud, String campo, int limite,
			boolean obligatorio, Map<String, String> errores) {
		Object valor = solicitud.get(campo);
		if (!solicitud.containsKey(campo) && !obligatorio) {
			return "";
		}
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
		String normalizado = normalizarTexto(contenido);
		if (obligatorio && normalizado.isEmpty()) {
			errores.putIfAbsent(campo, "Completa este campo.");
		}
		validarLongitud(normalizado, campo, limite, errores);
		return normalizado;
	}

	private static void validarLongitud(String texto, String campo, int limite, Map<String, String> errores) {
		if (texto.codePointCount(0, texto.length()) > limite) {
			errores.putIfAbsent(campo, "Este campo admite hasta " + limite + " caracteres.");
		}
	}

	private static void validarEstado(String estado, Map<String, String> errores) {
		if (!Set.of("Activo", "Inactivo").contains(estado)) {
			errores.putIfAbsent("estado", "Selecciona Activo o Inactivo.");
		}
	}

	private static void lanzarErrores(Map<String, String> errores) {
		if (!errores.isEmpty()) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos del formulario.", errores);
		}
	}

}
