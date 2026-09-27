package com.dextre.medishift.sesion;

/** Los valores de JSON se verifican antes de convertirlos; no se registra la contraseña. */
public record SolicitudSesion(Object codigoInstitucion, Object correo, Object contrasena) {

	@Override
	public String toString() {
		return "SolicitudSesion[datos omitidos]";
	}

}
