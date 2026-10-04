package com.dextre.medishift.programacion;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;

@RestControllerAdvice(assignableTypes = ControladorProgramacion.class)
public class ManejadorErroresProgramacion {

	public record RespuestaError(String mensaje, Map<String, String> errores) { }

	@ExceptionHandler(CatalogoException.class)
	public ResponseEntity<RespuestaError> programacion(CatalogoException excepcion) {
		return responder(excepcion.obtenerEstado(), excepcion.getMessage(), excepcion.obtenerErrores());
	}

	@ExceptionHandler(AccesoNoAutorizadoException.class)
	public ResponseEntity<RespuestaError> noAutorizado() {
		return responder(HttpStatus.UNAUTHORIZED, "Tu sesión terminó. Inicia sesión nuevamente.", Map.of());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<RespuestaError> jsonInvalido() {
		return responder(HttpStatus.BAD_REQUEST, "Envía un objeto JSON válido con los datos del formulario.", Map.of());
	}

	@ExceptionHandler({ DataAccessException.class, TransactionException.class })
	public ResponseEntity<RespuestaError> baseDatos(Exception excepcion) {
		String estado = estadoSql(excepcion);
		if (excepcion instanceof DataAccessResourceFailureException
				|| excepcion instanceof TransientDataAccessResourceException
				|| excepcion instanceof CannotCreateTransactionException
				|| estado.startsWith("08") || estado.matches("57P0[123]")) {
			return responder(HttpStatus.SERVICE_UNAVAILABLE,
					"La base de datos no está disponible. Inténtalo nuevamente más tarde.", Map.of());
		}
		if (excepcion instanceof DuplicateKeyException || estado.startsWith("23")
				|| estado.equals("40001") || estado.equals("40P01")) {
			return responder(HttpStatus.CONFLICT,
					"Los datos cambiaron o el turno tiene un conflicto. Recarga el horario y revisa sus referencias.", Map.of());
		}
		return interno();
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<RespuestaError> otroError(Exception excepcion) {
		if (excepcion instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
			return responder(error.getStatusCode(), "La solicitud no tiene un formato o método permitido.", Map.of());
		}
		return interno();
	}

	private String estadoSql(Throwable excepcion) {
		Throwable causa = excepcion;
		for (int profundidad = 0; causa != null && profundidad < 16; profundidad++) {
			if (causa instanceof SQLException sql && sql.getSQLState() != null) { return sql.getSQLState(); }
			causa = causa.getCause();
		}
		return "";
	}

	private ResponseEntity<RespuestaError> interno() {
		return responder(HttpStatus.INTERNAL_SERVER_ERROR,
				"No se pudo completar la programación. Inténtalo nuevamente más tarde.", Map.of());
	}

	private ResponseEntity<RespuestaError> responder(HttpStatusCode estado, String mensaje, Map<String, String> errores) {
		return ResponseEntity.status(estado).header("Cache-Control", "no-store").body(new RespuestaError(mensaje, errores));
	}

}
