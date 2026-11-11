package com.dextre.medishift.sesion;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SesionesHttp {
    private static final String ATRIBUTO_CUENTA_PORTAL = "medishift.idCuentaPortalPaciente";

    private SesionesHttp() { }

    public static void abrirPortal(HttpServletRequest solicitud, UUID cuenta) {
        renovar(solicitud).setAttribute(ATRIBUTO_CUENTA_PORTAL, cuenta);
    }

    public static UUID identificarPortal(HttpSession sesion) {
        try {
            return sesion != null && sesion.getAttribute(ATRIBUTO_CUENTA_PORTAL) instanceof UUID cuenta ? cuenta : null;
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
