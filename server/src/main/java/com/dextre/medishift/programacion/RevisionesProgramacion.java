package com.dextre.medishift.programacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Requisito;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

public final class RevisionesProgramacion {

	private RevisionesProgramacion() { }

	public static String deTurno(Turno turno) {
		return resumir(turno.id(), turno.horario(), turno.profesional(), turno.consultorio(), turno.especialidad(),
				turno.inicio(), turno.fin(), turno.estado(), turno.observacion(), turno.nombreProfesional(),
				turno.nombreConsultorio(), turno.nombreEspecialidad());
	}

	public static String deSemana(Horario horario, List<Turno> turnos, List<Requisito> requisitos) {
		return resumir(horario, turnos.stream().map(RevisionesProgramacion::deTurno).toList(), requisitos);
	}

	public static String resumir(Object... valores) {
		try {
			MessageDigest resumen = MessageDigest.getInstance("SHA-256");
			for (Object valor : valores) {
				byte[] contenido = String.valueOf(valor).getBytes(StandardCharsets.UTF_8);
				resumen.update(Integer.toString(contenido.length).getBytes(StandardCharsets.US_ASCII));
				resumen.update((byte) ':');
				resumen.update(contenido);
			}
			return HexFormat.of().formatHex(resumen.digest());
		} catch (NoSuchAlgorithmException excepcion) {
			throw new IllegalStateException("El entorno no admite SHA-256.", excepcion);
		}
	}

	public static void comprobar(String recibida, String actual) {
		if (!actual.equals(recibida)) {
			throw CatalogoException.conflicto("revision", "El horario cambió. Recarga los datos antes de continuar.");
		}
	}

	public static String leerCondicion(String cabecera) {
		if (cabecera == null || cabecera.isBlank()) {
			throw new CatalogoException(HttpStatus.PRECONDITION_REQUIRED,
					"Recarga el turno antes de modificarlo.", java.util.Map.of("revision", "Envía If-Match con su revisión."));
		}
		if (!cabecera.matches("\"[a-f0-9]{64}\"")) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "La revisión no es válida.",
					java.util.Map.of("revision", "If-Match debe contener la revisión entre comillas."));
		}
		return cabecera.substring(1, cabecera.length() - 1);
	}

}
