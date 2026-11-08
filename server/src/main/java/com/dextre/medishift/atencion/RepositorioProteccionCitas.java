package com.dextre.medishift.atencion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;

/** Comparte el bloqueo institucional de programación; funciona también antes de V006. */
@Repository
public class RepositorioProteccionCitas {
    private final JdbcTemplate consultas;

    public RepositorioProteccionCitas(JdbcTemplate consultas) { this.consultas = consultas; }

    private boolean disponible() {
        return Boolean.TRUE.equals(consultas.queryForObject("SELECT to_regclass(current_schema() || '.cita') IS NOT NULL", Boolean.class));
    }

    public void exigirCambioPermitido(UUID institucion, UUID turno, UUID profesional, UUID consultorio, Instant inicio, Instant fin) {
        if (!disponible()) return;
        comprobar(consultas.query("""
                SELECT id,inicio,estado FROM cita WHERE id_institucion=? AND id_turno=? AND estado<>'cancelada'
                AND (id_profesional<>? OR id_consultorio<>? OR inicio<? OR fin>?) ORDER BY inicio,id LIMIT 6
                """, (fila, numero) -> fila.getObject("id") + " (" + fila.getTimestamp("inicio").toInstant() + ", " + fila.getString("estado") + ")",
                institucion, turno, profesional, consultorio, Timestamp.from(inicio), Timestamp.from(fin)));
    }

    public void exigirRetiroPermitido(UUID institucion, UUID turno) {
        if (!disponible()) return;
        comprobar(consultas.query("SELECT id,inicio,estado FROM cita WHERE id_institucion=? AND id_turno=? AND estado<>'cancelada' ORDER BY inicio,id LIMIT 6",
                (fila, numero) -> fila.getObject("id") + " (" + fila.getTimestamp("inicio").toInstant() + ", " + fila.getString("estado") + ")", institucion, turno));
    }

    public void exigirRetiroHorario(UUID institucion, UUID horario) {
        if (!disponible()) return;
        comprobar(consultas.query("""
                SELECT c.id,c.inicio,c.estado FROM cita c JOIN shift t ON t.id_institution=c.id_institucion AND t.id_shift=c.id_turno
                WHERE t.id_institution=? AND t.id_schedule=? AND c.estado<>'cancelada' ORDER BY c.inicio,c.id LIMIT 6
                """, (fila, numero) -> fila.getObject("id") + " (" + fila.getTimestamp("inicio").toInstant() + ", " + fila.getString("estado") + ")", institucion, horario));
    }

    /** Un turno con citas canceladas se retira sin borrar su historial. */
    public boolean conservarAlEliminar(UUID institucion, UUID turno) {
        if (!disponible()) return false;
        if (!Boolean.TRUE.equals(consultas.queryForObject("SELECT EXISTS (SELECT 1 FROM cita WHERE id_institucion=? AND id_turno=?)", Boolean.class, institucion, turno))) return false;
        consultas.update("UPDATE shift SET status_shift='cancelled' WHERE id_institution=? AND id_shift=?", institucion, turno);
        return true;
    }

    private void comprobar(List<String> afectadas) {
        if (!afectadas.isEmpty()) throw CatalogoException.conflicto("citas", "Resuelve las citas afectadas antes de cambiar el horario: "
                + String.join("; ", afectadas) + ". Consulta la agenda para reprogramarlas o cancelarlas.");
    }
}
