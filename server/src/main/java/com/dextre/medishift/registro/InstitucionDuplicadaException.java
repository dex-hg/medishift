package com.dextre.medishift.registro;

public class InstitucionDuplicadaException extends RuntimeException {

	public InstitucionDuplicadaException() {
		super("Ya existe una institución con ese código.");
	}

}
