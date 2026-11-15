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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;
import com.dextre.medishift.catalogos.DatosCatalogos.Etiqueta;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;
import com.dextre.medishift.programacion.RevisionesProgramacion;

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
		Profesional formulario = ValidadorCatalogos.validarProfesional(datos);
		Profesional creado = datos.containsKey("idsEtiquetas")
				? servicio.guardarProfesional(institucion, acceso.obtenerCuenta(solicitud), null, formulario,
						ValidadorCatalogos.idsEtiquetas(datos.get("idsEtiquetas")), null)
				: servicio.crearProfesional(institucion, formulario);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@PutMapping(value = "/profesionales/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Profesional actualizarProfesional(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		UUID profesional = ValidadorCatalogos.validarId(id);
		Profesional formulario = ValidadorCatalogos.validarProfesional(datos);
		if (datos.containsKey("idsEtiquetas")) {
			return servicio.guardarProfesional(institucion, acceso.obtenerCuenta(solicitud), profesional, formulario,
					ValidadorCatalogos.idsEtiquetas(datos.get("idsEtiquetas")), RevisionesProgramacion.leerCondicion(revision));
		}
		return servicio.actualizarProfesional(institucion, profesional, formulario);
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
		Consultorio formulario = ValidadorCatalogos.validarConsultorio(datos);
		Consultorio creado = datos.containsKey("idsEtiquetas")
				? servicio.guardarConsultorio(institucion, acceso.obtenerCuenta(solicitud), null, formulario,
						ValidadorCatalogos.idsEtiquetas(datos.get("idsEtiquetas")), null)
				: servicio.crearConsultorio(institucion, formulario);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@PutMapping(value = "/consultorios/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Consultorio actualizarConsultorio(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision,
			HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		UUID consultorio = ValidadorCatalogos.validarId(id);
		Consultorio formulario = ValidadorCatalogos.validarConsultorio(datos);
		if (datos.containsKey("idsEtiquetas")) {
			return servicio.guardarConsultorio(institucion, acceso.obtenerCuenta(solicitud), consultorio, formulario,
					ValidadorCatalogos.idsEtiquetas(datos.get("idsEtiquetas")), RevisionesProgramacion.leerCondicion(revision));
		}
		return servicio.actualizarConsultorio(institucion, consultorio, formulario);
	}

	@DeleteMapping("/consultorios/{id}")
	public ResponseEntity<Void> eliminarConsultorio(@PathVariable String id, HttpServletRequest solicitud) {
		servicio.eliminarConsultorio(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/especialidades")
	public List<Especialidad> listarEspecialidades(HttpServletRequest solicitud) {
		return servicio.listarEspecialidades(acceso.obtenerInstitucion(solicitud));
	}

	@PostMapping(value = "/especialidades", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Especialidad> crearEspecialidad(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearEspecialidad(institucion,
				ValidadorCatalogos.validarEspecialidad(datos)));
	}

	@GetMapping("/etiquetas")
	public List<Etiqueta> listarEtiquetas(HttpServletRequest solicitud) {
		return servicio.listarEtiquetas(acceso.obtenerInstitucion(solicitud));
	}

	@PostMapping(value = "/etiquetas", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Etiqueta> crearEtiqueta(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		Etiqueta etiqueta = servicio.crearEtiqueta(acceso.obtenerInstitucion(solicitud), acceso.obtenerCuenta(solicitud),
				ValidadorCatalogos.validarEspecialidad(datos));
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etiqueta.revision()).body(etiqueta);
	}

	@PutMapping(value = "/etiquetas/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Etiqueta> actualizarEtiqueta(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		Etiqueta etiqueta = servicio.actualizarEtiqueta(acceso.obtenerInstitucion(solicitud), acceso.obtenerCuenta(solicitud),
				ValidadorCatalogos.validarId(id), RevisionesProgramacion.leerCondicion(revision),
				ValidadorCatalogos.validarCambioEtiqueta(datos));
		return ResponseEntity.ok().eTag(etiqueta.revision()).body(etiqueta);
	}

}
