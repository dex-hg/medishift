package com.dextre.medishift.disponibilidad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;

public final class RevisionDisponibilidades {

	private RevisionDisponibilidades() {
	}

	public static String calcular(Disponibilidad disponibilidad) {
		String contenido = String.join("\u0000", disponibilidad.id().toString(),
				disponibilidad.idProfesional().toString(), String.valueOf(disponibilidad.diaSemana()),
				disponibilidad.horaInicio(), disponibilidad.horaFin(), disponibilidad.fechaInicio(),
				disponibilidad.fechaFin(), disponibilidad.estado(), disponibilidad.observacion());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(contenido.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException excepcion) {
			throw new IllegalStateException("El entorno no admite SHA-256.", excepcion);
		}
	}

	public static String validarCabecera(String cabecera) {
		if (cabecera == null || cabecera.isBlank()) {
			throw new CatalogoException(HttpStatus.PRECONDITION_REQUIRED,
					"Vuelve a cargar la disponibilidad antes de modificarla.", Map.of("revision", "Envía la revisión mediante If-Match."));
		}
		if (!cabecera.matches("\"[a-f0-9]{64}\"")) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "La revisión no tiene un formato válido.",
					Map.of("revision", "Envía una revisión SHA-256 entre comillas mediante If-Match."));
		}
		return cabecera.substring(1, cabecera.length() - 1);
	}

	public static void exigirActual(String esperada, Disponibilidad actual) {
		if (!actual.revision().equals(esperada)) {
			throw CatalogoException.conflicto("revision",
					"La disponibilidad cambió desde que la abriste. Vuelve a cargarla antes de continuar.");
		}
	}

}
