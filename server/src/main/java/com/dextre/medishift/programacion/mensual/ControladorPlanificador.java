package com.dextre.medishift.programacion.mensual;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

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

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.CalendarioMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.ConsultorioArea;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.TurnoMensual;

@RestController
@RequestMapping(value = "/api/planificador", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorPlanificador {

	private final AccesoCatalogos sesiones;
	private final ServicioAccesoMensual acceso;
	private final ServicioPlanificador servicio;

	public ControladorPlanificador(AccesoCatalogos sesiones, ServicioAccesoMensual acceso, ServicioPlanificador servicio) {
		this.sesiones = sesiones;
		this.acceso = acceso;
		this.servicio = servicio;
	}

	@GetMapping
	public CalendarioMensual consultar(@RequestParam(required = false) String periodo, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return servicio.consultar(contexto.institucion(), contexto.actor(), ValidadorPlanesMensuales.periodo(periodo));
	}

	@PostMapping(value = "/consultorios", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ConsultorioArea> crearConsultorio(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return ResponseEntity.status(201).body(servicio.crearConsultorio(contexto.institucion(), contexto.actor(),
				ValidadorPlanificador.consultorio(datos)));
	}

	@PutMapping(value = "/consultorios/{id}/area", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ConsultorioArea vincularConsultorio(@PathVariable String id, @RequestBody Map<String, Object> datos,
			HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return servicio.vincularConsultorio(contexto.institucion(), contexto.actor(),
				ValidadorPlanesMensuales.identificador(id, "id"), ValidadorPlanificador.area(datos));
	}

	@PostMapping(value = "/turnos", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TurnoMensual> crearTurno(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		TurnoMensual turno = servicio.crearTurno(contexto.institucion(), contexto.actor(), ValidadorPlanificador.turno(datos));
		return ResponseEntity.created(URI.create("/api/planificador/turnos/" + turno.id())).eTag(turno.revision()).body(turno);
	}

	@PutMapping(value = "/turnos/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TurnoMensual> actualizarTurno(@PathVariable String id, @RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		TurnoMensual turno = servicio.actualizarTurno(contexto.institucion(), contexto.actor(),
				ValidadorPlanesMensuales.identificador(id, "id"), RevisionesProgramacion.leerCondicion(revision),
				ValidadorPlanificador.turno(datos));
		return ResponseEntity.ok().eTag(turno.revision()).body(turno);
	}

	@DeleteMapping("/turnos/{id}")
	public ResponseEntity<Void> eliminarTurno(@PathVariable String id,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		servicio.eliminarTurno(contexto.institucion(), contexto.actor(), ValidadorPlanesMensuales.identificador(id, "id"),
				RevisionesProgramacion.leerCondicion(revision));
		return ResponseEntity.noContent().build();
	}

	private Contexto autorizar(HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		UUID actor = sesiones.obtenerCuenta(solicitud);
		acceso.exigirAdministrador(institucion, actor);
		return new Contexto(institucion, actor);
	}

	private record Contexto(UUID institucion, UUID actor) { }
}
