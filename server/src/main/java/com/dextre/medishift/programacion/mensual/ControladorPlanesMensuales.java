package com.dextre.medishift.programacion.mensual;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.ListadoPlanes;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.ListadoUnidades;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.UnidadPlanificacion;

@RestController
public class ControladorPlanesMensuales {

	private final AccesoCatalogos sesiones;
	private final ServicioAccesoMensual acceso;
	private final ServicioPlanesMensuales servicio;

	public ControladorPlanesMensuales(AccesoCatalogos sesiones, ServicioAccesoMensual acceso, ServicioPlanesMensuales servicio) {
		this.sesiones = sesiones;
		this.acceso = acceso;
		this.servicio = servicio;
	}

	@GetMapping("/api/unidades-planificacion")
	public ListadoUnidades unidades(HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return servicio.listarUnidades(contexto.institucion(), contexto.actor());
	}

	@PostMapping(value = "/api/unidades-planificacion", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UnidadPlanificacion> crearUnidad(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return ResponseEntity.status(201).body(servicio.crearUnidad(contexto.institucion(), contexto.actor(),
				ValidadorPlanesMensuales.unidad(datos)));
	}

	@GetMapping("/api/planes-mensuales")
	public ListadoPlanes listar(@RequestParam(required = false) String unidad, @RequestParam(required = false) String periodo,
			HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		return servicio.listarPlanes(contexto.institucion(), contexto.actor(),
				ValidadorPlanesMensuales.identificador(unidad, "idUnidad"), ValidadorPlanesMensuales.periodo(periodo));
	}

	@GetMapping("/api/planes-mensuales/{id}")
	public ResponseEntity<PlanMensual> buscar(@PathVariable String id, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		PlanMensual plan = servicio.buscarPlan(contexto.institucion(), contexto.actor(), ValidadorPlanesMensuales.identificador(id, "id"));
		return ResponseEntity.ok().eTag(plan.revision()).body(plan);
	}

	@PostMapping(value = "/api/planes-mensuales", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PlanMensual> crear(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
		Contexto contexto = autorizar(solicitud);
		PlanMensual plan = servicio.crearPlan(contexto.institucion(), contexto.actor(), ValidadorPlanesMensuales.plan(datos));
		return ResponseEntity.created(URI.create("/api/planes-mensuales/" + plan.id())).eTag(plan.revision()).body(plan);
	}

	private Contexto autorizar(HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		UUID actor = sesiones.obtenerCuenta(solicitud);
		acceso.exigirAdministrador(institucion, actor);
		return new Contexto(institucion, actor);
	}

	private record Contexto(UUID institucion, UUID actor) { }
}
