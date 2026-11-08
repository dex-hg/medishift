package com.dextre.medishift.atencion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.DatosAtencion.*;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@Service
public class ServicioPacientes {
    private final ServicioAccesoMensual acceso;
    private final RepositorioPacientes pacientes;
    private final RepositorioEspecialidades exclusiones;
    private final RepositorioProgramacion horarios;

    public ServicioPacientes(ServicioAccesoMensual acceso, RepositorioPacientes pacientes,
            RepositorioEspecialidades exclusiones, RepositorioProgramacion horarios) {
        this.acceso = acceso;
        this.pacientes = pacientes;
        this.exclusiones = exclusiones;
        this.horarios = horarios;
    }

    @Transactional(readOnly = true)
    public PaginaPacientes listar(UUID institucion, UUID actor, String buscar, int pagina) {
        var permisos = acceso.consultarProgramacion(institucion, actor);
        pacientes.verificarEsquema();
        return pacientes.listar(institucion, permisos.idProfesional(), buscar, pagina);
    }

    @Transactional(readOnly = true)
    public List<ResumenPaciente> buscarDni(UUID institucion, UUID actor, String dni) {
        var permisos = acceso.consultarProgramacion(institucion, actor);
        pacientes.verificarEsquema();
        return pacientes.buscarDni(institucion, permisos.idProfesional(), dni);
    }

    @Transactional(readOnly = true)
    public Paciente buscar(UUID institucion, UUID actor, UUID id) {
        var permisos = acceso.consultarProgramacion(institucion, actor);
        pacientes.verificarEsquema();
        if (!permisos.esAdministrador() && !pacientes.vinculado(institucion, id, permisos.idProfesional())) {
            throw CatalogoException.noEncontrado();
        }
        return pacientes.buscar(institucion, id);
    }

    @Transactional
    public Paciente crear(UUID institucion, UUID actor, SolicitudPaciente datos) {
        exclusiones.bloquearInstitucion(institucion);
        var permisos = acceso.consultarProgramacion(institucion, actor);
        pacientes.verificarEsquema();
        UUID id = UUID.randomUUID();
        validar(institucion, id, datos);
        pacientes.insertar(institucion, id, datos);
        if (!permisos.esAdministrador()) pacientes.vincular(institucion, id, permisos.idProfesional());
        pacientes.auditar(institucion, actor, id, "create");
        return pacientes.buscar(institucion, id);
    }

    @Transactional
    public Paciente actualizar(UUID institucion, UUID actor, UUID id, String revision, SolicitudPaciente datos) {
        exclusiones.bloquearInstitucion(institucion);
        Paciente actual = buscar(institucion, actor, id);
        RevisionesProgramacion.comprobar(revision, actual.revision());
        validar(institucion, id, datos);
        pacientes.actualizar(institucion, id, datos);
        pacientes.auditar(institucion, actor, id, "update");
        return pacientes.buscar(institucion, id);
    }

    private void validar(UUID institucion, UUID id, SolicitudPaciente datos) {
        if (datos.fechaNacimiento().isAfter(LocalDate.now(horarios.zona(institucion)))) {
            throw CatalogoException.conflicto("fechaNacimiento", "La fecha de nacimiento no puede ser futura.");
        }
        if (pacientes.dniRepetido(institucion, datos.dni(), id)) {
            throw CatalogoException.conflicto("dni", "El DNI ya está registrado en esta institución. Búscalo para reutilizar la ficha.");
        }
    }
}
