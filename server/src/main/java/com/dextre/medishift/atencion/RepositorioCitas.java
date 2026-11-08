package com.dextre.medishift.atencion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.atencion.DatosAtencion.*;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@Repository
public class RepositorioCitas {
    private final JdbcTemplate consultas;
    private static final String CONSULTA = """
            SELECT c.*, p.nombres || ' ' || p.apellidos AS paciente,p.codigo,
            m.first_name_professional || ' ' || m.last_name_professional AS profesional,
            r.code_room || ' · ' || r.name_room AS consultorio FROM cita c
            JOIN paciente p ON p.id_institucion=c.id_institucion AND p.id=c.id_paciente
            JOIN professional m ON m.id_institution=c.id_institucion AND m.id_professional=c.id_profesional
            JOIN room r ON r.id_institution=c.id_institucion AND r.id_room=c.id_consultorio
            WHERE c.id_institucion=?
            """;
    private static final String TURNOS = """
            SELECT t.*, p.first_name_professional || ' ' || p.last_name_professional AS profesional,
            r.code_room || ' · ' || r.name_room AS consultorio,e.name_specialty AS especialidad
            FROM shift t JOIN schedule h ON h.id_institution=t.id_institution AND h.id_schedule=t.id_schedule
            JOIN professional p ON p.id_institution=t.id_institution AND p.id_professional=t.id_professional
            JOIN room r ON r.id_institution=t.id_institution AND r.id_room=t.id_room
            JOIN specialty e ON e.id_specialty=t.id_specialty
            WHERE t.id_institution=? AND t.status_shift='approved' AND h.status_schedule='approved'
            AND p.status_professional='active' AND r.status_room='active'
            AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.id_shift=t.id_shift AND a.status_attendance='absent')
            """;

    public RepositorioCitas(JdbcTemplate consultas) { this.consultas = consultas; }

    public List<TurnoAgenda> turnos(UUID institucion, UUID profesional, Instant inicio, Instant fin) {
        String filtro = profesional == null ? "" : " AND t.id_professional=?";
        Object[] argumentos = profesional == null ? new Object[]{institucion, Timestamp.from(fin), Timestamp.from(inicio)}
                : new Object[]{institucion, Timestamp.from(fin), Timestamp.from(inicio), profesional};
        return consultas.query(TURNOS + " AND t.start_at_shift<? AND t.end_at_shift>?" + filtro + " ORDER BY t.start_at_shift,t.id_shift",
                (fila, numero) -> leerTurno(fila), argumentos);
    }

    public TurnoAgenda turno(UUID institucion, UUID id) {
        return consultas.query(TURNOS + " AND t.id_shift=?", (fila, numero) -> leerTurno(fila), institucion, id)
                .stream().findFirst().orElseThrow(() -> CatalogoException.conflicto("idTurno", "Selecciona un turno aprobado y vigente con recursos activos."));
    }

    public List<Cita> agenda(UUID institucion, UUID profesional, Instant inicio, Instant fin) {
        String filtro = profesional == null ? "" : " AND c.id_profesional=?";
        Object[] argumentos = profesional == null ? new Object[]{institucion, Timestamp.from(fin), Timestamp.from(inicio)}
                : new Object[]{institucion, Timestamp.from(fin), Timestamp.from(inicio), profesional};
        return consultas.query(CONSULTA + " AND c.inicio<? AND c.fin>?" + filtro + " ORDER BY c.inicio,c.id", (fila, numero) -> leer(fila), argumentos);
    }

    public HistorialCitas historial(UUID institucion, UUID paciente, UUID profesional, int pagina, String zona) {
        String filtro = " AND c.id_paciente=?" + (profesional == null ? "" : " AND c.id_profesional=?");
        var argumentos = new java.util.ArrayList<Object>(List.of(institucion, paciente));
        if (profesional != null) argumentos.add(profesional);
        long total = consultas.queryForObject("SELECT count(*) FROM cita c WHERE c.id_institucion=?" + filtro, Long.class, argumentos.toArray());
        argumentos.add(pagina * 30);
        return new HistorialCitas(consultas.query(CONSULTA + filtro + " ORDER BY c.inicio DESC,c.id LIMIT 30 OFFSET ?",
                (fila, numero) -> leer(fila), argumentos.toArray()), total, pagina, zona);
    }

    public Cita buscar(UUID institucion, UUID id) {
        return consultas.query(CONSULTA + " AND c.id=?", (fila, numero) -> leer(fila), institucion, id)
                .stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
    }

    public boolean cruce(UUID institucion, UUID paciente, UUID profesional, Instant inicio, Instant fin, UUID excluir) {
        return Boolean.TRUE.equals(consultas.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM cita WHERE id_institucion=? AND id<>? AND estado<>'cancelada'
                AND (id_paciente=? OR id_profesional=?) AND inicio<? AND fin>?)
                """, Boolean.class, institucion, excluir, paciente, profesional, Timestamp.from(fin), Timestamp.from(inicio)));
    }

    public void insertar(UUID institucion, UUID actor, UUID id, UUID paciente, TurnoAgenda turno, Instant inicio, Instant fin) {
        consultas.update("INSERT INTO cita(id,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador) VALUES (?,?,?,?,?,?,?,?,?)",
                id, institucion, paciente, turno.id(), turno.idProfesional(), turno.idConsultorio(), Timestamp.from(inicio), Timestamp.from(fin), actor);
    }

    public void reprogramar(UUID institucion, UUID id, TurnoAgenda turno, Instant inicio, Instant fin) {
        consultas.update("UPDATE cita SET id_turno=?,id_profesional=?,id_consultorio=?,inicio=?,fin=?,version=version+1 WHERE id_institucion=? AND id=?",
                turno.id(), turno.idProfesional(), turno.idConsultorio(), Timestamp.from(inicio), Timestamp.from(fin), institucion, id);
    }

    public void cambiarEstado(UUID institucion, UUID id, String estado) {
        consultas.update("UPDATE cita SET estado=?,version=version+1 WHERE id_institucion=? AND id=?", estado, institucion, id);
    }

    public void auditar(UUID institucion, UUID actor, UUID id, String accion) {
        consultas.update("""
                INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
                entity_id_audit_event,action_audit_event,after_audit_event)
                SELECT ?,?,?,'cita',?, ?,jsonb_build_object('idTurno',id_turno,'inicio',inicio,'fin',fin,'estado',estado,'version',version)
                FROM cita WHERE id_institucion=? AND id=?
                """, UUID.randomUUID(), institucion, actor, id, accion, institucion, id);
    }

    private Cita leer(ResultSet fila) throws SQLException {
        UUID id = fila.getObject("id", UUID.class);
        return new Cita(id, fila.getObject("id_paciente", UUID.class), fila.getString("paciente"), fila.getString("codigo"),
                fila.getObject("id_turno", UUID.class), fila.getObject("id_profesional", UUID.class), fila.getString("profesional"),
                fila.getObject("id_consultorio", UUID.class), fila.getString("consultorio"), fila.getTimestamp("inicio").toInstant(),
                fila.getTimestamp("fin").toInstant(), fila.getString("estado"), RevisionesProgramacion.resumir(id, fila.getLong("version")));
    }

    private TurnoAgenda leerTurno(ResultSet fila) throws SQLException {
        return new TurnoAgenda(fila.getObject("id_shift", UUID.class), fila.getObject("id_professional", UUID.class), fila.getString("profesional"),
                fila.getObject("id_room", UUID.class), fila.getString("consultorio"), fila.getString("especialidad"),
                fila.getTimestamp("start_at_shift").toInstant(), fila.getTimestamp("end_at_shift").toInstant());
    }
}
