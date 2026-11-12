package com.dextre.medishift.portalpaciente;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dextre.medishift.atencion.ValidadorAtencion;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.programacion.mensual.ValidadorPlanesMensuales;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.SesionesHttp;

@RestController
@RequestMapping("/api/portal-paciente")
public class ControladorPortalPaciente {
    private final ServicioPortalPaciente servicio;
    private final AccesoCatalogos acceso;

    public ControladorPortalPaciente(ServicioPortalPaciente servicio, AccesoCatalogos acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    @PostMapping(value = "/sesion", consumes = "application/json")
    public ResponseEntity<Void> iniciar(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
        acceso.validarOrigen(solicitud);
        var identidad = ValidadorAtencion.ingresoPaciente(datos);
        servicio.autenticar(identidad, solicitud.getRemoteAddr());
        SesionesHttp.abrirPortal(solicitud, identidad);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrar(HttpServletRequest solicitud) {
        acceso.validarOrigen(solicitud);
        HttpSession sesion = solicitud.getSession(false);
        if (SesionesHttp.identificarPortal(sesion) != null) SesionesHttp.invalidar(sesion);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendario")
    public ServicioPortalPaciente.Calendario calendario(@RequestParam String periodo, HttpServletRequest solicitud) {
        HttpSession sesion = solicitud.getSession(false);
        var identidad = SesionesHttp.identificarPortal(sesion);
        if (identidad == null) throw new AccesoNoAutorizadoException();
        try {
            return servicio.calendario(identidad, periodo, ValidadorPlanesMensuales.periodo(periodo));
        } catch (AccesoNoAutorizadoException fallo) {
            SesionesHttp.invalidar(sesion);
            throw fallo;
        }
    }
}
