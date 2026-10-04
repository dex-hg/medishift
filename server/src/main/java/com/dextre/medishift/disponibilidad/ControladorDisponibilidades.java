package com.dextre.medishift.disponibilidad;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;

@RestController
@RequestMapping(value = "/api/disponibilidades", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorDisponibilidades {

	private final AccesoCatalogos acceso;
	private final ServicioDisponibilidades servicio;

	public ControladorDisponibilidades(AccesoCatalogos acceso, ServicioDisponibilidades servicio) {
		this.acceso = acceso;
		this.servicio = servicio;
	}

	@GetMapping
	public List<Disponibilidad> listar(HttpServletRequest solicitud) {
		return servicio.listar(acceso.obtenerInstitucion(solicitud));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Disponibilidad> buscar(HttpServletRequest solicitud, @PathVariable("id") String identificador) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return responder(HttpStatus.OK, servicio.buscar(institucion, ValidadorCatalogos.validarId(identificador)));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Disponibilidad> crear(HttpServletRequest solicitud, @RequestBody Map<String, Object> contenido) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return responder(HttpStatus.CREATED, servicio.crear(institucion, ValidadorDisponibilidades.validar(contenido)));
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Disponibilidad> actualizar(HttpServletRequest solicitud, @PathVariable("id") String identificador,
			@RequestHeader(value = "If-Match", required = false) String cabecera, @RequestBody Map<String, Object> contenido) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return responder(HttpStatus.OK, servicio.actualizar(institucion, ValidadorCatalogos.validarId(identificador),
				RevisionDisponibilidades.validarCabecera(cabecera), ValidadorDisponibilidades.validar(contenido)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(HttpServletRequest solicitud, @PathVariable("id") String identificador,
			@RequestHeader(value = "If-Match", required = false) String cabecera) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		servicio.eliminar(institucion, ValidadorCatalogos.validarId(identificador), RevisionDisponibilidades.validarCabecera(cabecera));
		return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
	}

	private ResponseEntity<Disponibilidad> responder(HttpStatus estado, Disponibilidad disponibilidad) {
		return ResponseEntity.status(estado).header("Cache-Control", "no-store")
				.eTag(disponibilidad.revision()).body(disponibilidad);
	}

}
