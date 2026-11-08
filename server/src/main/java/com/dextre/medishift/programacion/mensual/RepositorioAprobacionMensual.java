package com.dextre.medishift.programacion.mensual;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.VersionArchivada;

@Repository
public class RepositorioAprobacionMensual {
    private final JdbcTemplate consultas;
    public RepositorioAprobacionMensual(JdbcTemplate consultas) { this.consultas = consultas; }

    public void aprobar(UUID institucion, UUID actor, UUID plan) {
        consultas.update("UPDATE schedule SET status_schedule='approved',id_approver_user_account=?,approved_at_schedule=now() WHERE id_institution=? AND id_schedule=?", actor, institucion, plan);
        consultas.update("UPDATE shift SET status_shift='approved' WHERE id_institution=? AND id_schedule=? AND status_shift IN ('draft','pending')", institucion, plan);
        auditar(institucion, actor, plan, "approve");
    }

    public void reabrir(UUID institucion, UUID actor, UUID plan) {
        consultas.update("""
                INSERT INTO historial_plan_mensual(id,id_institucion,id_plan,version,contenido,id_actor)
                SELECT ?,?,?,h.version_schedule,jsonb_build_object('plan',to_jsonb(h),'turnos',coalesce((
                SELECT jsonb_agg(to_jsonb(t) ORDER BY t.start_at_shift,t.id_shift) FROM shift t
                WHERE t.id_institution=h.id_institution AND t.id_schedule=h.id_schedule),'[]'::jsonb)),?
                FROM schedule h WHERE h.id_institution=? AND h.id_schedule=?
                """, UUID.randomUUID(), institucion, plan, actor, institucion, plan);
        consultas.update("UPDATE schedule SET status_schedule='draft',version_schedule=version_schedule+1,id_approver_user_account=NULL,approved_at_schedule=NULL WHERE id_institution=? AND id_schedule=?", institucion, plan);
        consultas.update("UPDATE shift SET status_shift='draft' WHERE id_institution=? AND id_schedule=? AND status_shift='approved'", institucion, plan);
        auditar(institucion, actor, plan, "reopen");
    }

    public List<VersionArchivada> historial(UUID institucion, UUID plan) {
        return consultas.query("SELECT version,archivado_en,id_actor,contenido::text FROM historial_plan_mensual WHERE id_institucion=? AND id_plan=? ORDER BY version DESC",
                (fila, numero) -> new VersionArchivada(fila.getInt("version"), fila.getTimestamp("archivado_en").toInstant(),
                        fila.getObject("id_actor", UUID.class), fila.getString("contenido")), institucion, plan);
    }

    private void auditar(UUID institucion, UUID actor, UUID plan, String accion) {
        consultas.update("""
                INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,entity_id_audit_event,action_audit_event,after_audit_event)
                SELECT ?,?,?,'schedule',?,?,to_jsonb(h) FROM schedule h WHERE h.id_institution=? AND h.id_schedule=?
                """, UUID.randomUUID(), institucion, actor, plan, accion, institucion, plan);
    }
}
