package com.dextre.medishift.registro;

/** Los valores se validan como texto antes de normalizarlos, sin coerción de JSON. */
public record SolicitudRegistro(Object codigoInstitucion, Object nombreInstitucion,
		Object zonaHoraria, Object correo, Object contrasena) {

	@Override
	public String toString() {
		return "SolicitudRegistro[datos omitidos]";
	}

}
