package com.dextre.medishift.acceso;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.registro.ValidadorRegistro;

public final class ValidadorAccesoDoctores {
	private ValidadorAccesoDoctores() { }

	public record Solicitud(String correo, String contrasena, Boolean habilitado) {
		@Override public String toString() { return "Solicitud[credenciales omitidas]"; }
	}

	public static Solicitud validarInicial(Object correo, Object contrasena) {
		return validarCredenciales(correo, contrasena, true, true, "correoAcceso", "contrasenaAcceso");
	}

	public static Solicitud validar(Map<String, Object> datos, boolean nuevaCuenta) {
		if (datos == null || !Set.of("correo", "contrasena", "habilitado").containsAll(datos.keySet())
				|| (datos.containsKey("habilitado") && !(datos.get("habilitado") instanceof Boolean))) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos del acceso.",
					Map.of("formulario", "Envía los datos de acceso con un formato válido."));
		}
		return validarCredenciales(datos.get("correo"), datos.get("contrasena"), (Boolean) datos.get("habilitado"),
				nuevaCuenta, "correo", "contrasena");
	}

	private static Solicitud validarCredenciales(Object valorCorreo, Object valorContrasena, Boolean habilitado,
			boolean nuevaCuenta, String campoCorreo, String campoContrasena) {
		String correo = valorCorreo instanceof String texto ? texto.strip().toLowerCase(Locale.ROOT) : "";
		String contrasena = valorContrasena instanceof String texto ? texto : "";
		Map<String, String> errores = new LinkedHashMap<>();
		if (nuevaCuenta && !Boolean.TRUE.equals(habilitado)) {
			errores.put("habilitado", "El profesional aún no tiene una cuenta de acceso.");
		}
		if (correo.length() > 254 || !ValidadorRegistro.tieneFormatoCorreo(correo)) {
			errores.put(campoCorreo, "Ingresa un correo válido.");
		}
		if (!(valorContrasena instanceof String) || ((nuevaCuenta || !contrasena.isEmpty())
				&& (contrasena.length() < 12 || contrasena.length() > 128 || contrasena.codePoints().allMatch(
						caracter -> Character.isWhitespace(caracter) || Character.isSpaceChar(caracter) || caracter == 0xfeff)
				|| contrasena.codePoints().anyMatch(caracter -> Character.isISOControl(caracter)
						|| Character.getType(caracter) == Character.SURROGATE)))) {
			errores.put(campoContrasena, "Usa entre 12 y 128 caracteres sin caracteres de control.");
		}
		if (!errores.isEmpty()) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos del acceso.", errores);
		return new Solicitud(correo, contrasena, habilitado);
	}
}
