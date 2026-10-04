package com.dextre.medishift.sesion;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FiltroNoCacheSesion extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
			FilterChain filtros) throws ServletException, IOException {
		String ruta = solicitud.getRequestURI().substring(solicitud.getContextPath().length());
		if (ruta.equals("/api/sesion") || ruta.equals("/api/especialidades")
				|| ruta.equals("/api/profesionales") || ruta.startsWith("/api/profesionales/")
				|| ruta.equals("/api/consultorios") || ruta.startsWith("/api/consultorios/")
				|| ruta.equals("/api/disponibilidades") || ruta.startsWith("/api/disponibilidades/")
				|| ruta.equals("/api/horarios") || ruta.startsWith("/api/horarios/")) {
			respuesta.setHeader("Cache-Control", "no-store");
		}
		filtros.doFilter(solicitud, respuesta);
	}

}
