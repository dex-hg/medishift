package com.dextre.medishift.sesion;

import java.util.UUID;

public record RespuestaSesion(UUID idCuenta, UUID idInstitucion,
		String codigoInstitucion, String nombreInstitucion, String correo) {
}
