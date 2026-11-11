package com.dextre.medishift.portalpaciente;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@RestController
@RequestMapping("/api/pacientes/{id}/acceso-portal")
public class ControladorAccesoPaciente {
    private final AccesoCatalogos acceso;
    private final ServicioPortalPaciente portal;

    public ControladorAccesoPaciente(AccesoCatalogos acceso, ServicioPortalPaciente portal) {
        this.acceso = acceso;
        this.portal = portal;
    }

    @GetMapping
    public ServicioPortalPaciente.EstadoAcceso estado(@PathVariable String id, HttpServletRequest solicitud) {
        return portal.estado(acceso.obtenerInstitucion(solicitud), acceso.obtenerCuenta(solicitud),
                ValidadorCatalogos.validarId(id));
    }

    @PostMapping
    public ServicioPortalPaciente.CodigoEmitido emitir(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
        return portal.emitir(acceso.obtenerInstitucion(solicitud), acceso.obtenerCuenta(solicitud),
                ValidadorCatalogos.validarId(id), RevisionesProgramacion.leerCondicion(revision));
    }
}
