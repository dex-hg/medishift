package com.dextre.medishift.catalogos;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class CatalogoException extends RuntimeException {

	private final HttpStatus estado;
	private final Map<String, String> errores;

	public CatalogoException(HttpStatus estado, String mensaje, Map<String, String> errores) {
		super(mensaje);
		this.estado = estado;
		this.errores = Map.copyOf(errores);
	}

	public HttpStatus obtenerEstado() {
		return estado;
	}

	public Map<String, String> obtenerErrores() {
		return errores;
	}

	public static CatalogoException noEncontrado() {
		return new CatalogoException(HttpStatus.NOT_FOUND, "El registro no existe en esta institución.", Map.of());
	}

	public static CatalogoException conflicto(String campo, String mensaje) {
		return new CatalogoException(HttpStatus.CONFLICT, mensaje, Map.of(campo, mensaje));
	}

	public static CatalogoException accesoDenegado() {
		return new CatalogoException(HttpStatus.FORBIDDEN,
				"La programación mensual requiere el rol Administrador.", Map.of());
	}

	public static CatalogoException noDisponible(String mensaje) {
		return new CatalogoException(HttpStatus.SERVICE_UNAVAILABLE, mensaje, Map.of());
	}

}
