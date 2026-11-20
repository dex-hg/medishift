package com.dextre.medishift.catalogos;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.ServicioSesion;
import com.dextre.medishift.sesion.SesionesHttp;

@Component
public class AccesoCatalogos {

	private final ServicioSesion sesiones;

	public AccesoCatalogos(ServicioSesion sesiones) {
		this.sesiones = sesiones;
	}

	public UUID obtenerInstitucion(HttpServletRequest solicitud) {
		HttpSession sesion = solicitud.getSession(false);
		if (sesion == null || SesionesHttp.identificarPortal(sesion) != null) {
			throw new AccesoNoAutorizadoException();
		}
		try {
			Object idCuenta = sesion.getAttribute("medishift.idCuenta");
			Object idInstitucion = sesion.getAttribute("medishift.idInstitucion");
			if (idCuenta instanceof UUID cuenta && idInstitucion instanceof UUID institucion
					&& sesiones.consultar(institucion, cuenta).isPresent()) {
				return institucion;
			}
			sesion.invalidate();
		} catch (IllegalStateException excepcion) {
			// Otra solicitud puede invalidar la sesión mientras se consulta la cuenta.
		}
		throw new AccesoNoAutorizadoException();
	}

	/** El actor procede exclusivamente de la sesión autenticada, nunca del formulario. */
	public UUID obtenerCuenta(HttpServletRequest solicitud) {
		obtenerInstitucion(solicitud);
		try {
			HttpSession sesion = solicitud.getSession(false);
			if (sesion != null && sesion.getAttribute("medishift.idCuenta") instanceof UUID cuenta) {
				return cuenta;
			}
		} catch (IllegalStateException excepcion) {
			// Una revocación concurrente tampoco permite registrar un actor sin sesión.
		}
		throw new AccesoNoAutorizadoException();
	}

}
