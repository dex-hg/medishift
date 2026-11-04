package com.dextre.medishift.configuracion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.AccesoCatalogos;

/** Las rutas operativas anteriores también quedan protegidas al incorporar doctores. */
@Configuration
public class ConfiguracionPermisosOperativos implements WebMvcConfigurer {
	private final AccesoCatalogos sesiones;
	private final ServicioAccesoMensual permisos;

	public ConfiguracionPermisosOperativos(AccesoCatalogos sesiones, ServicioAccesoMensual permisos) {
		this.sesiones = sesiones;
		this.permisos = permisos;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest solicitud, HttpServletResponse respuesta, Object controlador) {
				if (solicitud.getRequestURI().endsWith("/api/configuracion") && "GET".equals(solicitud.getMethod())) return true;
				permisos.exigirAdministrador(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud));
				return true;
			}
		}).addPathPatterns("/api/horarios", "/api/horarios/**", "/api/profesionales", "/api/profesionales/**",
				"/api/configuracion", "/api/consultorios", "/api/consultorios/**", "/api/especialidades", "/api/especialidades/**");
	}
}
