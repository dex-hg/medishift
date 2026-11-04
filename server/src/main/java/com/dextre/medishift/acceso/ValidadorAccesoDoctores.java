package com.dextre.medishift.acceso;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import com.dextre.medishift.catalogos.CatalogoException;

public final class ValidadorAccesoDoctores {
	private ValidadorAccesoDoctores() { }

	public record Solicitud(String correo, String contrasena, boolean habilitado) {
		@Override public String toString() { return "Solicitud[credenciales omitidas]"; }
	}

	public static Solicitud validar(Map<String, Object> datos, boolean nuevaCuenta) {
		if (datos == null || !datos.keySet().equals(Set.of("correo", "contrasena", "habilitado"))
				|| !(datos.get("habilitado") instanceof Boolean) || !(datos.get("correo") instanceof String)
				|| !(datos.get("contrasena") instanceof String)) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos del acceso.",
					Map.of("formulario", "Envía correo, contraseña y habilitación válidos."));
		}
		String correo = ((String) datos.get("correo")).strip().toLowerCase(Locale.ROOT);
		String contrasena = (String) datos.get("contrasena");
		boolean habilitado = Boolean.TRUE.equals(datos.get("habilitado"));
		Map<String, String> errores = new LinkedHashMap<>();
		if (nuevaCuenta) {
			if (!habilitado) errores.put("habilitado", "El profesional aún no tiene una cuenta de acceso.");
			if (correo.length() > 254 || !correo.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
					|| correo.codePoints().anyMatch(Character::isISOControl)) errores.put("correo", "Ingresa un correo válido.");
			if (contrasena.isBlank() || contrasena.length() < 12 || contrasena.length() > 128
					|| contrasena.codePoints().anyMatch(Character::isISOControl)) {
				errores.put("contrasena", "Usa entre 12 y 128 caracteres sin caracteres de control.");
			}
		}
		if (!errores.isEmpty()) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos del acceso.", errores);
		return new Solicitud(correo, contrasena, habilitado);
	}
}
