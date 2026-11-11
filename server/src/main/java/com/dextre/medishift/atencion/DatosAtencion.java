package com.dextre.medishift.atencion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Fichas administrativas e historial de citas, sin información clínica. */
public final class DatosAtencion {
    private DatosAtencion() { }

    public record SolicitudPaciente(String nombres, String apellidos, String dni, LocalDate fechaNacimiento,
            String correo, String telefono, boolean activo) { }
    public record Paciente(UUID id, String codigo, String nombres, String apellidos, String dni,
            LocalDate fechaNacimiento, String correo, String telefono, boolean activo, String revision) { }
    public record ResumenPaciente(UUID id, String codigo, String nombres, String apellidos, String dni,
            boolean activo, boolean puedeConsultar) { }
    public record PaginaPacientes(List<ResumenPaciente> pacientes, long total, int pagina) { }
    public record SolicitudCita(UUID idPaciente, UUID idTurno, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) { }
    public record TurnoAgenda(UUID id, UUID idProfesional, String profesional, UUID idConsultorio,
            String consultorio, String especialidad, Instant inicio, Instant fin) { }
    public record Cita(UUID id, UUID idPaciente, String paciente, String codigoPaciente, UUID idTurno,
            UUID idProfesional, String profesional, UUID idConsultorio, String consultorio,
            UUID idArea, String area, Instant inicio, Instant fin, String estado, String revision) { }
    public record Agenda(LocalDate fecha, String zonaHoraria, List<TurnoAgenda> turnos, List<Cita> citas) { }
    public record CalendarioAgenda(String periodo, String zonaHoraria, List<TurnoAgenda> turnos, List<Cita> citas) { }
    public record IdentidadDni(String dni, String nombres, String apellidos) { }
    public record HistorialCitas(List<Cita> citas, long total, int pagina, String zonaHoraria) { }
}
