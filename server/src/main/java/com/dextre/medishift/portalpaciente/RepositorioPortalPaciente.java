package com.dextre.medishift.portalpaciente;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.programacion.mensual.PeriodoMensual;
import com.dextre.medishift.sesion.IdentidadPortalPaciente;

@Repository
public class RepositorioPortalPaciente {
    private static final String FICHAS_INSTITUCIONALES = """
            FROM paciente p
            JOIN institution i ON i.id_institution=p.id_institucion AND i.status_institution='active'
            """;
    private static final String COINCIDENCIA = """
            WHERE p.activo AND p.dni=? AND p.fecha_nacimiento=?
            """;

    private final JdbcTemplate consultas;

    public record Hospital(String codigo, String nombre, String zonaHoraria) { }

    public record CitaPortal(UUID id, String codigoHospital, String consultorio, String ubicacion, String profesional,
            Instant inicio, Instant fin, String estado) { }

    public RepositorioPortalPaciente(JdbcTemplate consultas) {
        this.consultas = consultas;
    }

    public boolean existeFichaActiva(IdentidadPortalPaciente identidad) {
        return Boolean.TRUE.equals(consultas.queryForObject(
                "SELECT EXISTS (SELECT 1 " + FICHAS_INSTITUCIONALES + COINCIDENCIA + ")",
                Boolean.class, identidad.dni(), identidad.fechaNacimiento()));
    }

    public List<Hospital> hospitales(IdentidadPortalPaciente identidad) {
        return consultas.query("""
                SELECT i.code_institution,i.name_institution,i.time_zone_institution
                """ + FICHAS_INSTITUCIONALES + COINCIDENCIA + """
                ORDER BY i.name_institution,i.code_institution
                """, (fila, numero) -> new Hospital(fila.getString("code_institution"),
                fila.getString("name_institution"), fila.getString("time_zone_institution")),
                identidad.dni(), identidad.fechaNacimiento());
    }

    public List<CitaPortal> citas(IdentidadPortalPaciente identidad, PeriodoMensual periodo) {
        Instant desde = periodo.inicio().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hasta = periodo.finExclusivo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return consultas.query("""
                SELECT c.id,c.inicio,c.fin,c.estado,i.code_institution,r.location_room,
                    r.code_room || ' · ' || r.name_room AS consultorio,
                    m.first_name_professional || ' ' || m.last_name_professional AS profesional
                """ + FICHAS_INSTITUCIONALES + """
                JOIN cita c ON c.id_institucion=p.id_institucion AND c.id_paciente=p.id
                JOIN professional m ON m.id_institution=c.id_institucion AND m.id_professional=c.id_profesional
                JOIN room r ON r.id_institution=c.id_institucion AND r.id_room=c.id_consultorio
                """ + COINCIDENCIA + """
                    AND c.inicio>=? AND c.inicio<?
                    AND (c.inicio AT TIME ZONE i.time_zone_institution)::date >= ?
                    AND (c.inicio AT TIME ZONE i.time_zone_institution)::date < ?
                ORDER BY c.inicio,c.id
                """, (fila, numero) -> new CitaPortal(fila.getObject("id", UUID.class),
                fila.getString("code_institution"), fila.getString("consultorio"), fila.getString("location_room"),
                fila.getString("profesional"), fila.getTimestamp("inicio").toInstant(),
                fila.getTimestamp("fin").toInstant(), fila.getString("estado")),
                identidad.dni(), identidad.fechaNacimiento(), Timestamp.from(desde), Timestamp.from(hasta),
                periodo.inicio(), periodo.finExclusivo());
    }
}
