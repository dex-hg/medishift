package com.dextre.medishift.registro;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.seguridad.LimitadorSolicitudes;
import com.dextre.medishift.seguridad.LimitadorSolicitudes.Operacion;

@RestController
@RequestMapping("/api/registro")
public class ControladorRegistro {

	private final ServicioRegistro servicio;
	private final LimitadorSolicitudes limites;

	public ControladorRegistro(ServicioRegistro servicio, LimitadorSolicitudes limites) {
		this.servicio = servicio;
		this.limites = limites;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RespuestaRegistro> registrar(@RequestBody SolicitudRegistro solicitud,
			HttpServletRequest solicitudHttp) {
		limites.reservar(Operacion.REGISTRO, solicitudHttp.getRemoteAddr(), null);
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(solicitud));
	}

}
