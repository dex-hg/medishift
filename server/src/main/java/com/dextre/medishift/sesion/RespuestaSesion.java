package com.dextre.medishift.sesion;

import java.util.UUID;

public record RespuestaSesion(UUID idCuenta, UUID idInstitucion,
		String codigoInstitucion, String nombreInstitucion, String correo, boolean esAdministrador,
		boolean esDoctor, UUID idProfesional) {
	public RespuestaSesion(UUID cuenta, UUID institucion, String codigo, String nombre, String correo, boolean administrador) {
		this(cuenta, institucion, codigo, nombre, correo, administrador, false, null);
	}
}
