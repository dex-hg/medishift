package com.dextre.medishift.registro;

import java.util.Map;

public class ValidacionRegistroException extends RuntimeException {

	private final Map<String, String> errores;

	public ValidacionRegistroException(Map<String, String> errores) {
		super("Revisa los datos del registro.");
		this.errores = Map.copyOf(errores);
	}

	public Map<String, String> obtenerErrores() {
		return errores;
	}

}
