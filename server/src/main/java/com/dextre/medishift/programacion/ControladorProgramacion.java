package com.dextre.medishift.programacion;

import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.DatosProgramacion.Recursos;
import com.dextre.medishift.programacion.DatosProgramacion.SemanaVista;
import com.dextre.medishift.programacion.DatosProgramacion.TurnoVista;

@RestController
@RequestMapping(value = "/api/horarios", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorProgramacion {

	private final AccesoCatalogos acceso;
	private final ServicioProgramacion servicio;

	public ControladorProgramacion(AccesoCatalogos acceso, ServicioProgramacion servicio) {
		this.acceso = acceso;
		this.servicio = servicio;
	}

	@GetMapping
	public SemanaVista listar(@RequestParam(required = false) String fechaInicio, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		LocalDate inicio = fechaInicio == null ? null : ValidadorProgramacion.validarInicio(fechaInicio);
		return servicio.listarSemana(institucion, inicio);
	}

	@GetMapping("/recursos")
	public Recursos recursos(HttpServletRequest solicitud) {
		return servicio.listarRecursos(acceso.obtenerInstitucion(solicitud));
	}

	@GetMapping("/{id}")
	public ResponseEntity<TurnoVista> buscar(@PathVariable String id, HttpServletRequest solicitud) {
		TurnoVista turno = servicio.buscarTurno(acceso.obtenerInstitucion(solicitud), ValidadorCatalogos.validarId(id));
		return ResponseEntity.ok().eTag(turno.revision()).body(turno);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TurnoVista> crear(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		TurnoVista turno = servicio.crearTurno(institucion, acceso.obtenerCuenta(solicitud),
				ValidadorProgramacion.validarTurno(datos));
		return ResponseEntity.status(HttpStatus.CREATED).eTag(turno.revision()).body(turno);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TurnoVista> actualizar(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String condicion, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		TurnoVista turno = servicio.actualizarTurno(institucion, ValidadorCatalogos.validarId(id),
				RevisionesProgramacion.leerCondicion(condicion), ValidadorProgramacion.validarTurno(datos));
		return ResponseEntity.ok().eTag(turno.revision()).body(turno);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable String id,
			@RequestHeader(value = "If-Match", required = false) String condicion, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		servicio.eliminarTurno(institucion, ValidadorCatalogos.validarId(id), RevisionesProgramacion.leerCondicion(condicion));
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/aprobar", consumes = MediaType.APPLICATION_JSON_VALUE)
	public SemanaVista aprobar(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return servicio.aprobarSemana(institucion, acceso.obtenerCuenta(solicitud), ValidadorProgramacion.validarSemana(datos));
	}

	@PostMapping(value = "/reabrir", consumes = MediaType.APPLICATION_JSON_VALUE)
	public SemanaVista reabrir(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		UUID institucion = acceso.obtenerInstitucion(solicitud);
		return servicio.reabrirSemana(institucion, acceso.obtenerCuenta(solicitud), ValidadorProgramacion.validarSemana(datos));
	}

	@PostMapping(value = "/cancelar", consumes = MediaType.APPLICATION_JSON_VALUE)
	public SemanaVista cancelar(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		return servicio.cancelarSemana(acceso.obtenerInstitucion(solicitud), ValidadorProgramacion.validarSemana(datos));
	}

}
