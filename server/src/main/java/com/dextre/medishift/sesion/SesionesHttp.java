package com.dextre.medishift.sesion;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SesionesHttp {
    private static final String ATRIBUTO_IDENTIDAD_PORTAL = "medishift.identidadPortalPaciente";
    private static final Duration MAXIMO_PERSONAL = Duration.ofHours(8);
    private static final Duration MAXIMO_PORTAL = Duration.ofHours(1);
    private static final Duration INACTIVIDAD_PORTAL = Duration.ofMinutes(15);

    private SesionesHttp() { }

    public static void abrirPortal(HttpServletRequest solicitud, IdentidadPortalPaciente identidad) {
        HttpSession sesion = renovar(solicitud);
        sesion.setAttribute(ATRIBUTO_IDENTIDAD_PORTAL, identidad);
        sesion.setMaxInactiveInterval((int) INACTIVIDAD_PORTAL.toSeconds());
    }

    public static void expirarSiCorresponde(HttpSession sesion) {
        if (sesion == null) return;
        try {
            Duration maximo = identificarPortal(sesion) == null ? MAXIMO_PERSONAL : MAXIMO_PORTAL;
            if (System.currentTimeMillis() - sesion.getCreationTime() >= maximo.toMillis()) {
                invalidar(sesion);
            }
        } catch (IllegalStateException fallo) {
            // Otra solicitud ya cerró la sesión.
        }
    }

    public static IdentidadPortalPaciente identificarPortal(HttpSession sesion) {
        try {
            return sesion != null && sesion.getAttribute(ATRIBUTO_IDENTIDAD_PORTAL) instanceof IdentidadPortalPaciente identidad
                    ? identidad : null;
        } catch (IllegalStateException fallo) {
            return null;
        }
    }

    public static HttpSession renovar(HttpServletRequest solicitud) {
        invalidar(solicitud.getSession(false));
        HttpSession sesion = solicitud.getSession(true);
        solicitud.changeSessionId();
        sesion.setMaxInactiveInterval(30 * 60);
        return sesion;
    }

    public static void invalidar(HttpSession sesion) {
        if (sesion == null) return;
        try {
            sesion.invalidate();
        } catch (IllegalStateException fallo) {
            // Una solicitud simultánea ya cerró la sesión.
        }
    }
}
