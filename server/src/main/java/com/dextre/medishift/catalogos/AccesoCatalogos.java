package com.dextre.medishift.catalogos;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.ServicioSesion;

@Component
public class AccesoCatalogos {

	private final ServicioSesion sesiones;
	private final Set<String> origenesPermitidos;

	public AccesoCatalogos(ServicioSesion sesiones,
			@Value("${medishift.origenes-permitidos:http://localhost:5173,http://127.0.0.1:5173}")
			String origenesPermitidos) {
		this.sesiones = sesiones;
		this.origenesPermitidos = Arrays.stream(origenesPermitidos.split(","))
				.map(String::trim).filter(origen -> !origen.isEmpty()).collect(Collectors.toUnmodifiableSet());
	}

	public UUID obtenerInstitucion(HttpServletRequest solicitud) {
		HttpSession sesion = solicitud.getSession(false);
		if (sesion == null) {
			throw new AccesoNoAutorizadoException();
		}
		try {
			Object idCuenta = sesion.getAttribute("medishift.idCuenta");
			Object idInstitucion = sesion.getAttribute("medishift.idInstitucion");
			if (idCuenta instanceof UUID cuenta && idInstitucion instanceof UUID institucion
					&& sesiones.consultar(institucion, cuenta).isPresent()) {
				validarOrigen(solicitud);
				return institucion;
			}
			sesion.invalidate();
		} catch (IllegalStateException excepcion) {
			// Otra solicitud puede invalidar la sesión mientras se consulta la cuenta.
		}
		throw new AccesoNoAutorizadoException();
	}

	private void validarOrigen(HttpServletRequest solicitud) {
		if (Set.of("GET", "HEAD", "OPTIONS").contains(solicitud.getMethod())) {
			return;
		}
		String origen = solicitud.getHeader("Origin");
		if (origen == null) {
			if ("cross-site".equalsIgnoreCase(solicitud.getHeader("Sec-Fetch-Site"))) {
				throw origenNoPermitido();
			}
			return;
		}
		try {
			URI direccion = URI.create(origen);
			boolean direccionValida = ("http".equals(direccion.getScheme()) || "https".equals(direccion.getScheme()))
					&& direccion.getHost() != null && direccion.getRawUserInfo() == null
					&& direccion.getRawQuery() == null && direccion.getRawFragment() == null
					&& (direccion.getRawPath() == null || direccion.getRawPath().isEmpty());
			int puerto = direccion.getPort() == -1
					? ("https".equals(direccion.getScheme()) ? 443 : 80) : direccion.getPort();
			boolean mismoOrigen = solicitud.getScheme().equals(direccion.getScheme())
					&& direccion.getHost() != null && direccion.getHost().equalsIgnoreCase(solicitud.getServerName())
					&& puerto == solicitud.getServerPort();
			if (direccionValida && (mismoOrigen || origenesPermitidos.contains(origen))) {
				return;
			}
		} catch (IllegalArgumentException excepcion) {
			// Los encabezados malformados y el origen opaco "null" no se admiten.
		}
		throw origenNoPermitido();
	}

	private CatalogoException origenNoPermitido() {
		return new CatalogoException(HttpStatus.FORBIDDEN, "El origen de la solicitud no está permitido.", Map.of());
	}

}
