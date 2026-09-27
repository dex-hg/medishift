package com.dextre.medishift.registro;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registro")
public class ControladorRegistro {

	private final ServicioRegistro servicio;

	public ControladorRegistro(ServicioRegistro servicio) {
		this.servicio = servicio;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RespuestaRegistro> registrar(@RequestBody SolicitudRegistro solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(solicitud));
	}

}
