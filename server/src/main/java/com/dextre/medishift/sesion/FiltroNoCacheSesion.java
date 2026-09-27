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
		if (solicitud.getRequestURI().equals(solicitud.getContextPath() + "/api/sesion")) {
			respuesta.setHeader("Cache-Control", "no-store");
		}
		filtros.doFilter(solicitud, respuesta);
	}

}
