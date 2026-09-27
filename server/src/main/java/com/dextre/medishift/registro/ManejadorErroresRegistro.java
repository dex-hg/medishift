package com.dextre.medishift.registro;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
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

@RestControllerAdvice(assignableTypes = ControladorRegistro.class)
public class ManejadorErroresRegistro {

	public record RespuestaError(String mensaje, Map<String, String> errores) {
	}

	@ExceptionHandler(ValidacionRegistroException.class)
	public ResponseEntity<RespuestaError> validar(ValidacionRegistroException excepcion) {
		return responder(HttpStatus.BAD_REQUEST, excepcion.getMessage(), excepcion.obtenerErrores());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<RespuestaError> jsonInvalido() {
		return responder(HttpStatus.BAD_REQUEST, "Envía un objeto JSON válido con los datos del registro.", Map.of());
	}

	@ExceptionHandler(InstitucionDuplicadaException.class)
	public ResponseEntity<RespuestaError> duplicado(InstitucionDuplicadaException excepcion) {
		return responder(HttpStatus.CONFLICT, excepcion.getMessage(),
				Map.of("codigoInstitucion", excepcion.getMessage()));
	}

	@ExceptionHandler({ DataAccessException.class, TransactionException.class })
	public ResponseEntity<RespuestaError> baseDatos(Exception excepcion) {
		if (excepcion instanceof DataAccessResourceFailureException
				|| excepcion instanceof TransientDataAccessResourceException
				|| excepcion instanceof CannotCreateTransactionException || esFalloConexion(excepcion)) {
			return responder(HttpStatus.SERVICE_UNAVAILABLE,
					"La base de datos no está disponible. Inténtalo nuevamente más tarde.", Map.of());
		}
		return responderErrorInterno();
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<RespuestaError> errorInterno(Exception excepcion) {
		if (excepcion instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
			return responder(error.getStatusCode(), "La solicitud no tiene un formato o método permitido.", Map.of());
		}
		return responderErrorInterno();
	}

	private ResponseEntity<RespuestaError> responderErrorInterno() {
		return responder(HttpStatus.INTERNAL_SERVER_ERROR,
				"No se pudo completar el registro. Inténtalo nuevamente más tarde.", Map.of());
	}

	private boolean esFalloConexion(Throwable excepcion) {
		Throwable causa = excepcion;
		for (int profundidad = 0; causa != null && profundidad < 16; profundidad++) {
			if (causa instanceof SQLException errorSql) {
				String estado = errorSql.getSQLState();
				if (estado != null && (estado.startsWith("08")
						|| estado.equals("57P01") || estado.equals("57P02") || estado.equals("57P03"))) {
					return true;
				}
			}
			causa = causa.getCause();
		}
		return false;
	}

	private ResponseEntity<RespuestaError> responder(HttpStatusCode estado, String mensaje,
			Map<String, String> errores) {
		return ResponseEntity.status(estado).body(new RespuestaError(mensaje, errores));
	}

}
