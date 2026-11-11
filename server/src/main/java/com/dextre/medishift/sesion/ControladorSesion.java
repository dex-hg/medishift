package com.dextre.medishift.sesion;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sesion")
public class ControladorSesion {

	static final String ATRIBUTO_ID_CUENTA = "medishift.idCuenta";
	static final String ATRIBUTO_ID_INSTITUCION = "medishift.idInstitucion";
	private final ServicioSesion servicio;

	public ControladorSesion(ServicioSesion servicio) {
		this.servicio = servicio;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RespuestaSesion> iniciar(@RequestBody SolicitudSesion datos,
			HttpServletRequest solicitudHttp) {
		RespuestaSesion perfil = servicio.autenticar(datos);
		HttpSession sesion = SesionesHttp.renovar(solicitudHttp);
		sesion.setAttribute(ATRIBUTO_ID_CUENTA, perfil.idCuenta());
		sesion.setAttribute(ATRIBUTO_ID_INSTITUCION, perfil.idInstitucion());
		return ResponseEntity.status(HttpStatus.OK).body(perfil);
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RespuestaSesion> consultar(HttpServletRequest solicitudHttp) {
		HttpSession sesion = solicitudHttp.getSession(false);
		if (sesion == null || SesionesHttp.identificarPortal(sesion) != null) {
			throw new AccesoNoAutorizadoException();
		}
		try {
			Object idCuenta = sesion.getAttribute(ATRIBUTO_ID_CUENTA);
			Object idInstitucion = sesion.getAttribute(ATRIBUTO_ID_INSTITUCION);
			if (idCuenta instanceof UUID cuenta && idInstitucion instanceof UUID institucion) {
				return ResponseEntity.ok(servicio.consultar(institucion, cuenta).orElseThrow(() -> {
					sesion.invalidate();
					return new AccesoNoAutorizadoException();
				}));
			}
			sesion.invalidate();
		} catch (IllegalStateException excepcion) {
			// La sesión pudo invalidarse en otra solicitud simultánea.
		}
		throw new AccesoNoAutorizadoException();
	}

	@DeleteMapping
	public ResponseEntity<Void> cerrar(HttpServletRequest solicitudHttp) {
		HttpSession sesion = solicitudHttp.getSession(false);
		if (SesionesHttp.identificarPortal(sesion) == null) SesionesHttp.invalidar(sesion);
		return ResponseEntity.noContent().build();
	}

}
