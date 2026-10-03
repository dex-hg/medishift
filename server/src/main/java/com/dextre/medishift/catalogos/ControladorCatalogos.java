package com.dextre.medishift.catalogos;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorCatalogos {

	private final AccesoCatalogos acceso;
	private final ServicioCatalogos servicio;

	public ControladorCatalogos(AccesoCatalogos acceso, ServicioCatalogos servicio) {
		this.acceso = acceso;
		this.servicio = servicio;
	}

	@GetMapping("/profesionales")
	public List<Profesional> listarProfesionales(HttpServletRequest solicitud) {
		return servicio.listarProfesionales(acceso.obtenerInstitucion(solicitud));
	}

	@GetMapping("/profesionales/{id}")
	public Profesional buscarProfesional(@PathVariable String id, HttpServletRequest solicitud) {
		return servicio.buscarProfesional(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
	}

	@PostMapping(value = "/profesionales", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Profesional> crearProfesional(@RequestBody Map<String, Object> datos,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(servicio.crearProfesional(institucion, ValidadorCatalogos.validarProfesional(datos)));
	}

	@PutMapping(value = "/profesionales/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Profesional actualizarProfesional(@PathVariable String id, @RequestBody Map<String, Object> datos,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return servicio.actualizarProfesional(institucion, ValidadorCatalogos.validarId(id),
				ValidadorCatalogos.validarProfesional(datos));
	}

	@DeleteMapping("/profesionales/{id}")
	public ResponseEntity<Void> eliminarProfesional(@PathVariable String id, HttpServletRequest solicitud) {
		servicio.eliminarProfesional(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/consultorios")
	public List<Consultorio> listarConsultorios(HttpServletRequest solicitud) {
		return servicio.listarConsultorios(acceso.obtenerInstitucion(solicitud));
	}

	@GetMapping("/consultorios/{id}")
	public Consultorio buscarConsultorio(@PathVariable String id, HttpServletRequest solicitud) {
		return servicio.buscarConsultorio(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
	}

	@PostMapping(value = "/consultorios", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Consultorio> crearConsultorio(@RequestBody Map<String, Object> datos,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(servicio.crearConsultorio(institucion, ValidadorCatalogos.validarConsultorio(datos)));
	}

	@PutMapping(value = "/consultorios/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Consultorio actualizarConsultorio(@PathVariable String id, @RequestBody Map<String, Object> datos,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return servicio.actualizarConsultorio(institucion, ValidadorCatalogos.validarId(id),
				ValidadorCatalogos.validarConsultorio(datos));
	}

	@DeleteMapping("/consultorios/{id}")
	public ResponseEntity<Void> eliminarConsultorio(@PathVariable String id, HttpServletRequest solicitud) {
		servicio.eliminarConsultorio(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/especialidades")
	public List<Especialidad> listarEspecialidades(HttpServletRequest solicitud) {
		acceso.obtenerInstitucion(solicitud);
		return servicio.listarEspecialidades();
	}

}
