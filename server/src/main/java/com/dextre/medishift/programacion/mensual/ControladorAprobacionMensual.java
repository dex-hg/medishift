package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.VersionArchivada;

@RestController
@RequestMapping(value = "/api/planes-mensuales/{id}", produces = "application/json")
public class ControladorAprobacionMensual {
    private final AccesoCatalogos sesiones;
    private final ServicioAprobacionMensual aprobaciones;

    public ControladorAprobacionMensual(AccesoCatalogos sesiones, ServicioAprobacionMensual aprobaciones) {
        this.sesiones = sesiones; this.aprobaciones = aprobaciones;
    }

    @PostMapping("/aprobar")
    public ResponseEntity<PlanMensual> aprobar(@PathVariable String id, @RequestHeader(value = "If-Match", required = false) String revision,
            HttpServletRequest solicitud) { return cambiar(id, revision, solicitud, false); }

    @PostMapping("/reabrir")
    public ResponseEntity<PlanMensual> reabrir(@PathVariable String id, @RequestHeader(value = "If-Match", required = false) String revision,
            HttpServletRequest solicitud) { return cambiar(id, revision, solicitud, true); }

    @GetMapping("/historial")
    public List<VersionArchivada> historial(@PathVariable String id, HttpServletRequest solicitud) {
        return aprobaciones.historial(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud), ValidadorCatalogos.validarId(id));
    }

    private ResponseEntity<PlanMensual> cambiar(String id, String revision, HttpServletRequest solicitud, boolean reabrir) {
        UUID institucion = sesiones.obtenerInstitucion(solicitud);
        PlanMensual plan = aprobaciones.cambiar(institucion, sesiones.obtenerCuenta(solicitud), ValidadorCatalogos.validarId(id),
                RevisionesProgramacion.leerCondicion(revision), reabrir);
        return ResponseEntity.ok().eTag(plan.revision()).body(plan);
    }
}
