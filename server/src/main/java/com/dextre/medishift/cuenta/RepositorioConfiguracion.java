package com.dextre.medishift.cuenta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.cuenta.DatosConfiguracion.Cuenta;
import com.dextre.medishift.cuenta.DatosConfiguracion.Institucion;
import com.dextre.medishift.cuenta.DatosConfiguracion.Preferencias;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Repository
public class RepositorioConfiguracion {

	private final JdbcTemplate consultas;

	public RepositorioConfiguracion(JdbcTemplate consultas) { this.consultas = consultas; }

	public Institucion institucion(UUID institucion) {
		return consultas.query("""
				SELECT i.name_institution, i.time_zone_institution,
				coalesce(c.daily_minutes,360) AS diarios, coalesce(c.weekly_minutes,2160) AS semanales,
				coalesce(c.holiday_credit_minutes,360) AS feriados, coalesce(c.revision_settings,0) AS revision
				FROM institution i LEFT JOIN institution_settings c USING (id_institution)
				WHERE i.id_institution=?
				""", (fila, numero) -> new Institucion(fila.getString(1), fila.getString(2), fila.getInt(3),
				fila.getInt(4), fila.getInt(5), RevisionesProgramacion.resumir(institucion,
						fila.getString(1), fila.getString(2), fila.getInt(3), fila.getInt(4), fila.getInt(5), fila.getLong(6))),
				institucion).stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public Cuenta cuenta(UUID institucion, UUID actor) {
		return consultas.query("""
				SELECT u.email_user_account,u.created_at_user_account,coalesce(c.display_name,'') AS nombre,
				coalesce(c.sidebar_compact,false) AS compacta,coalesce(c.default_start_time,time '08:00') AS inicio,
				coalesce(c.default_end_time,time '12:00') AS fin,coalesce(c.revision_settings,0) AS revision,
				EXISTS (SELECT 1 FROM user_role ur JOIN role r USING (id_role)
				WHERE ur.id_user_account=u.id_user_account AND r.code_role='administrador') AS administrador
				FROM user_account u LEFT JOIN account_settings c
				ON c.id_institution=u.id_institution AND c.id_user_account=u.id_user_account
				WHERE u.id_institution=? AND u.id_user_account=? AND u.status_user_account='active'
				""", (fila, numero) -> leerCuenta(fila, institucion, actor), institucion, actor)
				.stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	private Cuenta leerCuenta(ResultSet fila, UUID institucion, UUID actor) throws SQLException {
		Preferencias preferencias = new Preferencias(fila.getBoolean("compacta"),
				fila.getObject("inicio", LocalTime.class).toString(), fila.getObject("fin", LocalTime.class).toString());
		String revision = RevisionesProgramacion.resumir(institucion, actor, fila.getString("nombre"),
				fila.getString("email_user_account"), preferencias, fila.getLong("revision"));
		return new Cuenta(fila.getString("nombre"), fila.getString("email_user_account"),
				fila.getTimestamp("created_at_user_account").toInstant(), fila.getBoolean("administrador"), preferencias, revision);
	}

	public void bloquearCuenta(UUID institucion, UUID actor) {
		consultas.queryForObject("SELECT id_user_account FROM user_account WHERE id_institution=? AND id_user_account=? FOR UPDATE",
				UUID.class, institucion, actor);
	}

	public void guardarInstitucion(UUID institucion, Institucion datos) {
		consultas.update("UPDATE institution SET name_institution=?,time_zone_institution=? WHERE id_institution=?",
				datos.nombreInstitucion(), datos.zonaHoraria(), institucion);
		consultas.update("""
				INSERT INTO institution_settings (id_institution,daily_minutes,weekly_minutes,holiday_credit_minutes)
				VALUES (?,?,?,?) ON CONFLICT (id_institution) DO UPDATE SET daily_minutes=excluded.daily_minutes,
				weekly_minutes=excluded.weekly_minutes,holiday_credit_minutes=excluded.holiday_credit_minutes,
				revision_settings=institution_settings.revision_settings+1
				""", institucion, datos.minutosDiarios(), datos.minutosSemanales(), datos.minutosPorFeriado());
	}

	public boolean tieneProgramacion(UUID institucion) {
		return Boolean.TRUE.equals(consultas.queryForObject("SELECT EXISTS(SELECT 1 FROM schedule WHERE id_institution=?)",
				Boolean.class, institucion));
	}

	public List<Turno> jornadasDesde(UUID institucion, Instant inicio) {
		return consultas.query("""
				SELECT t.* FROM shift t JOIN schedule h USING(id_institution,id_schedule)
				WHERE t.id_institution=? AND t.end_at_shift>?
				AND t.status_shift IN ('draft','pending','approved') AND h.status_schedule IN ('draft','pending','approved')
				""", (fila, numero) -> new Turno(fila.getObject("id_shift", UUID.class), fila.getObject("id_schedule", UUID.class),
				fila.getObject("id_professional", UUID.class), fila.getObject("id_room", UUID.class), fila.getObject("id_specialty", UUID.class),
				fila.getTimestamp("start_at_shift").toInstant(), fila.getTimestamp("end_at_shift").toInstant(),
				fila.getString("status_shift"), fila.getString("note_shift"), "", "", ""), institucion, Timestamp.from(inicio));
	}

	public void guardarPerfil(UUID institucion, UUID actor, String nombre) {
		consultas.update("""
				INSERT INTO account_settings (id_institution,id_user_account,display_name) VALUES (?,?,?)
				ON CONFLICT (id_user_account) DO UPDATE SET display_name=excluded.display_name,
				revision_settings=account_settings.revision_settings+1
				""", institucion, actor, nombre);
	}

	public void guardarPreferencias(UUID institucion, UUID actor, Preferencias datos) {
		consultas.update("""
				INSERT INTO account_settings (id_institution,id_user_account,sidebar_compact,default_start_time,default_end_time)
				VALUES (?,?,?,?,?) ON CONFLICT (id_user_account) DO UPDATE SET sidebar_compact=excluded.sidebar_compact,
				default_start_time=excluded.default_start_time,default_end_time=excluded.default_end_time,
				revision_settings=account_settings.revision_settings+1
				""", institucion, actor, datos.barraCompacta(), LocalTime.parse(datos.horaInicio()), LocalTime.parse(datos.horaFin()));
	}

	public String hashContrasena(UUID institucion, UUID actor) {
		return consultas.queryForObject("SELECT password_hash_user_account FROM user_account WHERE id_institution=? AND id_user_account=?",
				String.class, institucion, actor);
	}

	public void guardarContrasena(UUID institucion, UUID actor, String hash) {
		consultas.update("UPDATE user_account SET password_hash_user_account=? WHERE id_institution=? AND id_user_account=?",
				hash, institucion, actor);
		consultas.update("""
				INSERT INTO account_settings (id_institution,id_user_account) VALUES (?,?)
				ON CONFLICT (id_user_account) DO UPDATE SET revision_settings=account_settings.revision_settings+1
				""", institucion, actor);
	}

	/** Las instantáneas excluyen contraseñas y hashes incluso al cambiar una clave. */
	public String captura(UUID institucion, UUID actor) {
		if (actor == null) return consultas.queryForObject("""
				SELECT jsonb_build_object('nombre',i.name_institution,'zonaHoraria',i.time_zone_institution,
				'politica',to_jsonb(c))::text FROM institution i LEFT JOIN institution_settings c USING(id_institution)
				WHERE i.id_institution=?
				""", String.class, institucion);
		return consultas.queryForObject("""
				SELECT jsonb_build_object('correo',u.email_user_account,'preferencias',to_jsonb(c))::text
				FROM user_account u LEFT JOIN account_settings c USING(id_institution,id_user_account)
				WHERE u.id_institution=? AND u.id_user_account=?
				""", String.class, institucion, actor);
	}

	public void auditar(UUID institucion, UUID actor, boolean institucional, String accion, String antes, String despues) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
				entity_id_audit_event,action_audit_event,before_audit_event,after_audit_event)
				VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb)
				""", UUID.randomUUID(), institucion, actor, institucional ? "institution_settings" : "account_settings",
				institucional ? institucion : actor, accion, antes, despues);
	}
}
