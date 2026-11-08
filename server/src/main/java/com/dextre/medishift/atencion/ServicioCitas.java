package com.dextre.medishift.atencion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.acceso.ServicioAccesoMensual.PermisosProgramacion;
import com.dextre.medishift.atencion.DatosAtencion.*;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.RepositorioRestriccionesProgramacion;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.TiempoProgramacion;

@Service
public class ServicioCitas {
    private final ServicioAccesoMensual acceso;
    private final RepositorioCitas citas;
    private final RepositorioPacientes pacientes;
    private final ServicioPacientes fichas;
    private final RepositorioEspecialidades exclusiones;
    private final RepositorioProgramacion horarios;
    private final RepositorioRestriccionesProgramacion restricciones;

    public ServicioCitas(ServicioAccesoMensual acceso, RepositorioCitas citas, RepositorioPacientes pacientes,
            ServicioPacientes fichas, RepositorioEspecialidades exclusiones, RepositorioProgramacion horarios,
            RepositorioRestriccionesProgramacion restricciones) {
        this.acceso = acceso;
        this.citas = citas;
        this.pacientes = pacientes;
        this.fichas = fichas;
        this.exclusiones = exclusiones;
        this.horarios = horarios;
        this.restricciones = restricciones;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Agenda agenda(UUID institucion, UUID actor, LocalDate fecha) {
        var permisos = preparar(institucion, actor);
        ZoneId zona = horarios.zona(institucion);
        Instant inicio = fecha.atStartOfDay(zona).toInstant();
        Instant fin = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        return new Agenda(fecha, zona.getId(), citas.turnos(institucion, permisos.idProfesional(), inicio, fin),
                citas.agenda(institucion, permisos.idProfesional(), inicio, fin));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HistorialCitas historial(UUID institucion, UUID actor, UUID paciente, int pagina) {
        var permisos = preparar(institucion, actor);
        fichas.buscar(institucion, actor, paciente);
        return citas.historial(institucion, paciente, permisos.idProfesional(), pagina, horarios.zona(institucion).getId());
    }

    @Transactional(readOnly = true)
    public Cita buscar(UUID institucion, UUID actor, UUID id) {
        var permisos = preparar(institucion, actor);
        Cita cita = citas.buscar(institucion, id);
        exigirPropia(permisos, cita.idProfesional());
        return cita;
    }

    @Transactional
    public Cita crear(UUID institucion, UUID actor, SolicitudCita datos) {
        exclusiones.bloquearInstitucion(institucion);
        var permisos = preparar(institucion, actor);
        UUID id = UUID.randomUUID();
        guardar(institucion, actor, id, datos, permisos, true);
        return citas.buscar(institucion, id);
    }

    @Transactional
    public Cita reprogramar(UUID institucion, UUID actor, UUID id, String revision, SolicitudCita datos) {
        exclusiones.bloquearInstitucion(institucion);
        Cita actual = buscar(institucion, actor, id);
        RevisionesProgramacion.comprobar(revision, actual.revision());
        exigirProgramada(actual);
        if (!actual.idPaciente().equals(datos.idPaciente())) throw CatalogoException.conflicto("idPaciente", "La reprogramación debe conservar al paciente.");
        guardar(institucion, actor, id, datos, preparar(institucion, actor), false);
        return citas.buscar(institucion, id);
    }

    @Transactional
    public Cita cambiarEstado(UUID institucion, UUID actor, UUID id, String revision, String estado) {
        exclusiones.bloquearInstitucion(institucion);
        Cita actual = buscar(institucion, actor, id);
        RevisionesProgramacion.comprobar(revision, actual.revision());
        exigirProgramada(actual);
        if (("atendida".equals(estado) && Instant.now().isBefore(actual.inicio()))
                || ("no_asistio".equals(estado) && Instant.now().isBefore(actual.fin()))) {
            throw CatalogoException.conflicto("estado", "Espera al inicio para marcar atendida o al final para registrar que no asistió.");
        }
        citas.cambiarEstado(institucion, id, estado);
        citas.auditar(institucion, actor, id, "change_status");
        return citas.buscar(institucion, id);
    }

    private void guardar(UUID institucion, UUID actor, UUID id, SolicitudCita datos, PermisosProgramacion permisos, boolean nueva) {
        TurnoAgenda turno = citas.turno(institucion, datos.idTurno());
        exigirPropia(permisos, turno.idProfesional());
        if (!pacientes.buscar(institucion, datos.idPaciente()).activo()) throw CatalogoException.conflicto("idPaciente", "El paciente está inactivo. Actualiza su ficha antes de agendar.");
        ZoneId zona = horarios.zona(institucion);
        Instant inicio = TiempoProgramacion.resolver(datos.fecha(), datos.horaInicio(), zona);
        Instant fin = TiempoProgramacion.resolver(datos.fecha(), datos.horaFin(), zona);
        if (!inicio.isAfter(Instant.now()) || !fin.isAfter(inicio) || inicio.isBefore(turno.inicio()) || fin.isAfter(turno.fin())) {
            throw CatalogoException.conflicto("horaInicio", "La cita debe ser futura y estar completamente dentro del turno aprobado.");
        }
        if (restricciones.tieneAusenciaAprobada(turno.idProfesional(), inicio, fin)
                || restricciones.tieneBloqueoActivo(turno.idConsultorio(), inicio, fin)) {
            throw CatalogoException.conflicto("idTurno", "El médico o consultorio no están disponibles en ese intervalo.");
        }
        if (citas.cruce(institucion, datos.idPaciente(), turno.idProfesional(), inicio, fin, id)) {
            throw CatalogoException.conflicto("horaInicio", "El médico o paciente ya tiene una cita que se cruza con este intervalo.");
        }
        if (nueva) citas.insertar(institucion, actor, id, datos.idPaciente(), turno, inicio, fin);
        else citas.reprogramar(institucion, id, turno, inicio, fin);
        citas.auditar(institucion, actor, id, nueva ? "create" : "reschedule");
    }

    private PermisosProgramacion preparar(UUID institucion, UUID actor) {
        var permisos = acceso.consultarProgramacion(institucion, actor);
        pacientes.verificarEsquema();
        return permisos;
    }

    private void exigirPropia(PermisosProgramacion permisos, UUID profesional) {
        if (!permisos.esAdministrador() && !profesional.equals(permisos.idProfesional())) throw CatalogoException.noEncontrado();
    }

    private void exigirProgramada(Cita cita) {
        if (!"programada".equals(cita.estado())) throw CatalogoException.conflicto("estado", "La cita ya tiene un estado final. Su historial se conserva.");
    }
}
