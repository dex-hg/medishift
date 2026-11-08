package com.dextre.medishift.atencion;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.dextre.medishift.atencion.DatosAtencion.*;
import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@RestController
@RequestMapping(value = "/api/pacientes", produces = "application/json")
public class ControladorPacientes {
    private final AccesoCatalogos sesiones;
    private final ServicioPacientes pacientes;
    private final ServicioCitas citas;

    public ControladorPacientes(AccesoCatalogos sesiones, ServicioPacientes pacientes, ServicioCitas citas) {
        this.sesiones = sesiones; this.pacientes = pacientes; this.citas = citas;
    }

    @GetMapping
    public PaginaPacientes listar(@RequestParam(required = false) String buscar, @RequestParam(required = false) String pagina,
            HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return pacientes.listar(contexto.institucion(), contexto.actor(), ValidadorAtencion.busqueda(buscar), ValidadorAtencion.pagina(pagina));
    }

    @GetMapping("/buscar-dni")
    public List<ResumenPaciente> buscarDni(@RequestParam String dni, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return pacientes.buscarDni(contexto.institucion(), contexto.actor(), ValidadorAtencion.dni(dni));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> buscar(@PathVariable String id, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        Paciente paciente = pacientes.buscar(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id));
        return ResponseEntity.ok().eTag(paciente.revision()).body(paciente);
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Paciente> crear(@RequestBody Map<String, Object> datos, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        Paciente paciente = pacientes.crear(contexto.institucion(), contexto.actor(), ValidadorAtencion.paciente(datos));
        return ResponseEntity.created(URI.create("/api/pacientes/" + paciente.id())).eTag(paciente.revision()).body(paciente);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ResponseEntity<Paciente> actualizar(@PathVariable String id, @RequestBody Map<String, Object> datos,
            @RequestHeader(value = "If-Match", required = false) String revision, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        Paciente paciente = pacientes.actualizar(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id),
                RevisionesProgramacion.leerCondicion(revision), ValidadorAtencion.paciente(datos));
        return ResponseEntity.ok().eTag(paciente.revision()).body(paciente);
    }

    @GetMapping("/{id}/citas")
    public HistorialCitas historial(@PathVariable String id, @RequestParam(required = false) String pagina, HttpServletRequest solicitud) {
        var contexto = identificar(solicitud);
        return citas.historial(contexto.institucion(), contexto.actor(), ValidadorCatalogos.validarId(id), ValidadorAtencion.pagina(pagina));
    }

    private Contexto identificar(HttpServletRequest solicitud) {
        return new Contexto(sesiones.obtenerInstitucion(solicitud), sesiones.obtenerCuenta(solicitud));
    }
    private record Contexto(UUID institucion, UUID actor) { }
}
