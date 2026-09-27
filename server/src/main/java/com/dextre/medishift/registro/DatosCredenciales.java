package com.dextre.medishift.registro;

public record DatosCredenciales(String codigoInstitucion, String correo, String contrasena) {

	@Override
	public String toString() {
		return "DatosCredenciales[datos omitidos]";
	}

}
