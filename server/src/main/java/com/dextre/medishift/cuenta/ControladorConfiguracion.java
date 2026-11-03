package com.dextre.medishift.cuenta;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.cuenta.DatosConfiguracion.Cuenta;
import com.dextre.medishift.cuenta.DatosConfiguracion.Institucion;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ControladorConfiguracion {

	private final AccesoCatalogos sesiones;
	private final ServicioConfiguracion servicio;

	public ControladorConfiguracion(AccesoCatalogos sesiones, ServicioConfiguracion servicio) {
		this.sesiones = sesiones;
		this.servicio = servicio;
	}

	@GetMapping("/cuenta")
	public ResponseEntity<Cuenta> cuenta(HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		return responderCuenta(servicio.cuenta(institucion, sesiones.obtenerCuenta(solicitud)));
	}

	@GetMapping("/configuracion")
	public ResponseEntity<Institucion> institucion(HttpServletRequest solicitud) {
		Institucion resultado = servicio.institucion(sesiones.obtenerInstitucion(solicitud));
		return ResponseEntity.ok().eTag(resultado.revision()).body(resultado);
	}

	@PutMapping(value = "/configuracion", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Institucion> guardarInstitucion(@RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		Institucion resultado = servicio.guardarInstitucion(institucion, sesiones.obtenerCuenta(solicitud),
				RevisionesProgramacion.leerCondicion(revision), ValidadorConfiguracion.institucion(datos));
		return ResponseEntity.ok().eTag(resultado.revision()).body(resultado);
	}

	@PutMapping(value = "/cuenta/perfil", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Cuenta> guardarPerfil(@RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		return responderCuenta(servicio.guardarPerfil(institucion, sesiones.obtenerCuenta(solicitud),
				RevisionesProgramacion.leerCondicion(revision), ValidadorConfiguracion.perfil(datos)));
	}

	@PutMapping(value = "/cuenta/preferencias", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Cuenta> guardarPreferencias(@RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		return responderCuenta(servicio.guardarPreferencias(institucion, sesiones.obtenerCuenta(solicitud),
				RevisionesProgramacion.leerCondicion(revision), ValidadorConfiguracion.preferencias(datos)));
	}

	@PutMapping(value = "/cuenta/contrasena", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Cuenta> cambiarContrasena(@RequestBody Map<String, Object> datos,
			@RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
		UUID institucion = sesiones.obtenerInstitucion(solicitud);
		Cuenta resultado = servicio.cambiarContrasena(institucion, sesiones.obtenerCuenta(solicitud),
				RevisionesProgramacion.leerCondicion(revision), ValidadorConfiguracion.claves(datos));
		solicitud.changeSessionId();
		return responderCuenta(resultado);
	}

	private ResponseEntity<Cuenta> responderCuenta(Cuenta cuenta) {
		return ResponseEntity.ok().eTag(cuenta.revision()).body(cuenta);
	}
}
