package com.dextre.medishift.registro;

public record DatosRegistro(String codigoInstitucion, String nombreInstitucion,
		String zonaHoraria, String correo, String contrasena) {

	@Override
	public String toString() {
		return "DatosRegistro[datos omitidos]";
	}

}
