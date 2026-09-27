package com.dextre.medishift.registro;

import java.util.UUID;

public record RespuestaRegistro(UUID idInstitucion, UUID idCuenta,
		String codigoInstitucion, String nombreInstitucion, String correo) {
}
