package com.dextre.medishift.seguridad;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorLimitesApi {

	@ExceptionHandler(LimitadorSolicitudes.LimiteAlcanzado.class)
	public ResponseEntity<Respuesta> limiteAlcanzado(LimitadorSolicitudes.LimiteAlcanzado fallo) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(fallo.segundos()))
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new Respuesta("Demasiados intentos. Espera antes de volver a intentarlo.", Map.of()));
	}

	record Respuesta(String mensaje, Map<String, String> errores) { }
}
