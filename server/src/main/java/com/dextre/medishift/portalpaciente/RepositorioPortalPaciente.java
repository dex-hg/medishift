package com.dextre.medishift.portalpaciente;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.programacion.mensual.PeriodoMensual;

@Repository
public class RepositorioPortalPaciente {
    private static final String HOSPITALES_DEL_PACIENTE = """
            FROM cuenta_portal_paciente cuenta
            JOIN paciente p ON p.dni=cuenta.dni AND p.activo
            JOIN institution i ON i.id_institution=p.id_institucion AND i.status_institution='active'
            """;

    private final JdbcTemplate consultas;

    public record Credencial(UUID id, String hash) {
        @Override public String toString() { return "Credencial[datos omitidos]"; }
    }

    public record Hospital(String codigo, String nombre, String zonaHoraria) { }

    public record CitaPortal(UUID id, String codigoHospital, String consultorio, String ubicacion, String profesional,
            Instant inicio, Instant fin, String estado) { }

    public RepositorioPortalPaciente(JdbcTemplate consultas) {
        this.consultas = consultas;
    }

    public void bloquearDni(String dni) {
        // Emisión y activación comparten el bloqueo aunque intervengan instituciones distintas.
        consultas.query("SELECT pg_advisory_xact_lock(723942, ?)", fila -> { }, Integer.parseInt(dni));
    }

    public Optional<Credencial> credencial(String dni) {
        return consultas.query("SELECT id,hash_contrasena FROM cuenta_portal_paciente WHERE dni=?",
                (fila, numero) -> new Credencial(fila.getObject("id", UUID.class), fila.getString("hash_contrasena")), dni)
                .stream().findFirst();
    }

    public boolean cuentaExiste(String dni) {
        return Boolean.TRUE.equals(consultas.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cuenta_portal_paciente WHERE dni=?)", Boolean.class, dni));
    }

    public boolean cuentaExiste(UUID cuenta) {
        return Boolean.TRUE.equals(consultas.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cuenta_portal_paciente WHERE id=?)", Boolean.class, cuenta));
    }

    public Instant vencimiento(UUID institucion, UUID paciente, String dni) {
        return consultas.query("""
                SELECT vence_en FROM codigo_activacion_paciente
                WHERE id_institucion=? AND id_paciente=? AND dni=? AND vence_en>now()
                """, (fila, numero) -> fila.getTimestamp("vence_en").toInstant(), institucion, paciente, dni)
                .stream().findFirst().orElse(null);
    }

    public Instant emitir(UUID institucion, UUID paciente, String dni, String hash) {
        return consultas.queryForObject("""
                INSERT INTO codigo_activacion_paciente(dni,id_institucion,id_paciente,hash_codigo,vence_en)
                VALUES (?,?,?,?,clock_timestamp()+interval '24 hours')
                ON CONFLICT (dni) DO UPDATE SET id_institucion=EXCLUDED.id_institucion,
                    id_paciente=EXCLUDED.id_paciente,hash_codigo=EXCLUDED.hash_codigo,vence_en=EXCLUDED.vence_en
                RETURNING vence_en
                """, (fila, numero) -> fila.getTimestamp("vence_en").toInstant(), dni, institucion, paciente, hash);
    }

    public boolean consumirCodigo(String dni, String hash) {
        // Durante la activación se comprueba la ficha que emitió el código y se conserva su identidad.
        boolean vigente = !consultas.query("""
                SELECT 1 FROM codigo_activacion_paciente a
                JOIN paciente p ON p.id_institucion=a.id_institucion AND p.id=a.id_paciente
                JOIN institution i ON i.id_institution=p.id_institucion
                WHERE a.dni=? AND a.hash_codigo=? AND a.vence_en>clock_timestamp()
                    AND p.dni=a.dni AND p.activo AND i.status_institution='active'
                FOR SHARE OF p,i
                """, (fila, numero) -> fila.getInt(1), dni, hash).isEmpty();
        if (vigente) consultas.update("DELETE FROM codigo_activacion_paciente WHERE dni=?", dni);
        return vigente;
    }

    public UUID crearCuenta(String dni, String hash) {
        UUID id = UUID.randomUUID();
        consultas.update("INSERT INTO cuenta_portal_paciente(id,dni,hash_contrasena) VALUES (?,?,?)", id, dni, hash);
        return id;
    }

    public List<Hospital> hospitales(UUID cuenta) {
        return consultas.query("""
                SELECT i.code_institution,i.name_institution,i.time_zone_institution
                """ + HOSPITALES_DEL_PACIENTE + """
                WHERE cuenta.id=? ORDER BY i.name_institution,i.code_institution
                """, (fila, numero) -> new Hospital(fila.getString("code_institution"),
                fila.getString("name_institution"), fila.getString("time_zone_institution")), cuenta);
    }

    public List<CitaPortal> citas(UUID cuenta, PeriodoMensual periodo) {
        Instant desde = periodo.inicio().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hasta = periodo.finExclusivo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return consultas.query("""
                SELECT c.id,c.inicio,c.fin,c.estado,i.code_institution,r.location_room,
                    r.code_room || ' · ' || r.name_room AS consultorio,
                    m.first_name_professional || ' ' || m.last_name_professional AS profesional
                """ + HOSPITALES_DEL_PACIENTE + """
                JOIN cita c ON c.id_institucion=p.id_institucion AND c.id_paciente=p.id
                JOIN professional m ON m.id_institution=c.id_institucion AND m.id_professional=c.id_profesional
                JOIN room r ON r.id_institution=c.id_institucion AND r.id_room=c.id_consultorio
                WHERE cuenta.id=? AND c.inicio>=? AND c.inicio<?
                    AND (c.inicio AT TIME ZONE i.time_zone_institution)::date >= ?
                    AND (c.inicio AT TIME ZONE i.time_zone_institution)::date < ?
                ORDER BY c.inicio,c.id
                """, (fila, numero) -> new CitaPortal(fila.getObject("id", UUID.class),
                fila.getString("code_institution"), fila.getString("consultorio"), fila.getString("location_room"),
                fila.getString("profesional"), fila.getTimestamp("inicio").toInstant(),
                fila.getTimestamp("fin").toInstant(), fila.getString("estado")),
                cuenta, Timestamp.from(desde), Timestamp.from(hasta), periodo.inicio(), periodo.finExclusivo());
    }
}
