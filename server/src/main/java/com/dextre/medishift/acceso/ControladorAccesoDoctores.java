package com.dextre.medishift.acceso;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.acceso.DatosAccesoDoctores.AccesoDoctor;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@RestController
@RequestMapping(value = "/api/profesionales/{id}/acceso", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorAccesoDoctores {

	private final AccesoCatalogos sesiones;
	private final ServicioAccesoDoctores servicio;

	public ControladorAccesoDoctores(AccesoCatalogos sesiones, ServicioAccesoDoctores servicio) {
		this.sesiones = sesiones;
		this.servicio = servicio;
	}

	@GetMapping
	public ResponseEntity<AccesoDoctor> consultar(@PathVariable String id, HttpServletRequest solicitud) {
		var acceso = servicio.consultar(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud),
				ValidadorCatalogos.validarId(id));
		return ResponseEntity.ok().eTag(acceso.revision()).body(acceso);
	}

	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AccesoDoctor> guardar(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		var acceso = servicio.guardar(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud),
				ValidadorCatalogos.validarId(id), RevisionesProgramacion.leerCondicion(revision), datos);
		return ResponseEntity.ok().eTag(acceso.revision()).body(acceso);
	}
}
