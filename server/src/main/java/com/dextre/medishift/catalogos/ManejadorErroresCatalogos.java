package com.dextre.medishift.catalogos;

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

import com.dextre.medishift.sesion.AccesoNoAutorizadoException;

@RestControllerAdvice(assignableTypes = ControladorCatalogos.class)
public class ManejadorErroresCatalogos {

	public record RespuestaError(String mensaje, Map<String, String> errores) {
	}

	@ExceptionHandler(CatalogoException.class)
	public ResponseEntity<RespuestaError> catalogo(CatalogoException excepcion) {
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
		String estadoSql = obtenerEstadoSql(excepcion);
		if (excepcion instanceof DataAccessResourceFailureException
				|| excepcion instanceof TransientDataAccessResourceException
				|| excepcion instanceof CannotCreateTransactionException
				|| estadoSql.startsWith("08") || estadoSql.matches("57P0[123]")) {
			return responder(HttpStatus.SERVICE_UNAVAILABLE,
					"La base de datos no está disponible. Inténtalo nuevamente más tarde.", Map.of());
		}
		if (excepcion instanceof DuplicateKeyException || "23505".equals(estadoSql)) {
			return responder(HttpStatus.CONFLICT, "Ya existe un registro con esos datos únicos.", Map.of());
		}
		if ("23503".equals(estadoSql)) {
			return responder(HttpStatus.CONFLICT,
					"El registro tiene datos vinculados. Puedes marcarlo como Inactivo.", Map.of());
		}
		return errorInterno();
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<RespuestaError> otroError(Exception excepcion) {
		if (excepcion instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
			return responder(error.getStatusCode(), "La solicitud no tiene un formato o método permitido.", Map.of());
		}
		return errorInterno();
	}

	private String obtenerEstadoSql(Throwable excepcion) {
		Throwable causa = excepcion;
		for (int profundidad = 0; causa != null && profundidad < 16; profundidad++) {
			if (causa instanceof SQLException errorSql && errorSql.getSQLState() != null) {
				return errorSql.getSQLState();
			}
			causa = causa.getCause();
		}
		return "";
	}

	private ResponseEntity<RespuestaError> errorInterno() {
		return responder(HttpStatus.INTERNAL_SERVER_ERROR,
				"No se pudo completar la solicitud. Inténtalo nuevamente más tarde.", Map.of());
	}

	private ResponseEntity<RespuestaError> responder(HttpStatusCode estado, String mensaje,
			Map<String, String> errores) {
		return ResponseEntity.status(estado).header("Cache-Control", "no-store")
				.body(new RespuestaError(mensaje, errores));
	}

}
