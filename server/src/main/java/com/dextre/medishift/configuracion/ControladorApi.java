package com.dextre.medishift.configuracion;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorApi {

	@GetMapping(path = { "/api", "/api/" }, produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> inicio() {
		return Map.of("servicio", "MediShift", "mensaje", "API de MediShift");
	}
}
