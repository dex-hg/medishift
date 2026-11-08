package com.dextre.medishift.atencion;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.dextre.medishift.atencion.DatosAtencion.Agenda;
import com.dextre.medishift.atencion.DatosAtencion.Cita;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@RestController
@RequestMapping(value = "/api/citas", produces = "application/json")
public class ControladorCitas {
    private final AccesoCatalogos sesiones;
    private final ServicioCitas citas;

    public ControladorCitas(AccesoCatalogos sesiones, ServicioCitas citas) { this.sesiones = sesiones; this.citas = citas; }

    @GetMapping
    public Agenda agenda(@RequestParam String fecha, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return citas.agenda(contexto.institucion(), contexto.actor(), ValidadorAtencion.fecha(fecha));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cita> buscar(@PathVariable String id, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return responder(citas.buscar(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id)));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Cita> crear(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        Cita cita = citas.crear(contexto.institucion(), contexto.actor(), ValidadorAtencion.cita(datos));
        return ResponseEntity.created(URI.create("/api/citas/" + cita.id())).eTag(cita.revision()).body(cita);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<Cita> reprogramar(@PathVariable String id, @RequestBody Map<String, Object> datos,
            @RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return responder(citas.reprogramar(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id),
                RevisionesProgramacion.leerCondicion(revision), ValidadorAtencion.cita(datos)));
    }

    @PutMapping(value = "/{id}/estado", consumes = "application/json")
    public ResponseEntity<Cita> cambiarEstado(@PathVariable String id, @RequestBody Map<String, Object> datos,
            @RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return responder(citas.cambiarEstado(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id),
                RevisionesProgramacion.leerCondicion(revision), ValidadorAtencion.estado(datos)));
    }

    private ResponseEntity<Cita> responder(Cita cita) { return ResponseEntity.ok().eTag(cita.revision()).body(cita); }
    private Contexto identificar(HttpServletRequest solicitud) {
        return new Contexto(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud));
    }
    private record Contexto(UUID institucion, UUID actor) { }
}
