package com.dextre.medishift.sesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SesionesHttp {
    private static final String ATRIBUTO_IDENTIDAD_PORTAL = "medishift.identidadPortalPaciente";

    private SesionesHttp() { }

    public static void abrirPortal(HttpServletRequest solicitud, IdentidadPortalPaciente identidad) {
        renovar(solicitud).setAttribute(ATRIBUTO_IDENTIDAD_PORTAL, identidad);
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
