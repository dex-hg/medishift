package com.dextre.medishift.sesion;

public class AccesoNoAutorizadoException extends RuntimeException {

	public AccesoNoAutorizadoException() {
		super("No se pudo autenticar la solicitud.");
	}

}
