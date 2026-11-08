package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.RepositorioPacientes;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.VersionArchivada;

@Service
public class ServicioAprobacionMensual {
    private final ServicioAccesoMensual acceso;
    private final RepositorioPlanesMensuales planes;
    private final RepositorioAprobacionMensual aprobaciones;
    private final ServicioPlanificador planificador;
    private final RepositorioEspecialidades exclusiones;
    private final RepositorioPacientes pacientes;

    public ServicioAprobacionMensual(ServicioAccesoMensual acceso, RepositorioPlanesMensuales planes,
            RepositorioAprobacionMensual aprobaciones, ServicioPlanificador planificador,
            RepositorioEspecialidades exclusiones, RepositorioPacientes pacientes) {
        this.acceso = acceso; this.planes = planes; this.aprobaciones = aprobaciones;
        this.planificador = planificador; this.exclusiones = exclusiones; this.pacientes = pacientes;
    }

    @Transactional
    public PlanMensual cambiar(UUID institucion, UUID actor, UUID id, String revision, boolean reabrir) {
        exclusiones.bloquearInstitucion(institucion);
        acceso.exigirAdministrador(institucion, actor);
        pacientes.verificarEsquema();
        PlanMensual plan = planes.buscarPlan(institucion, id);
        RevisionesProgramacion.comprobar(revision, plan.revision());
        if (reabrir) {
            if (!"Aprobado".equals(plan.estado())) throw CatalogoException.conflicto("estado", "Solo puedes reabrir un mes aprobado.");
            if (plan.version() == Integer.MAX_VALUE) throw CatalogoException.conflicto("version", "No se puede crear otra versión del mes.");
            aprobaciones.reabrir(institucion, actor, id);
        } else {
            if (!"Borrador".equals(plan.estado())) throw CatalogoException.conflicto("estado", "Solo puedes aprobar un mes en borrador.");
            planificador.validarAprobacion(institucion, plan);
            aprobaciones.aprobar(institucion, actor, id);
        }
        return planes.buscarPlan(institucion, id);
    }

    @Transactional(readOnly = true)
    public List<VersionArchivada> historial(UUID institucion, UUID actor, UUID id) {
        acceso.exigirAdministrador(institucion, actor);
        pacientes.verificarEsquema();
        planes.buscarPlan(institucion, id);
        return aprobaciones.historial(institucion, id);
    }
}
