package com.dextre.medishift.acceso;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.acceso.DatosAccesoDoctores.Vinculo;

@Repository
public class RepositorioAccesoDoctores {

	private final JdbcTemplate consultas;

	public RepositorioAccesoDoctores(JdbcTemplate consultas) { this.consultas = consultas; }

	public Vinculo buscar(UUID institucion, UUID profesional) {
		return consultas.query("""
				SELECT p.id_user_account,coalesce(u.email_user_account,p.email_professional) AS correo,
				u.status_user_account,p.status_professional,EXISTS (
				 SELECT 1 FROM user_role ur JOIN role r USING(id_role)
				 WHERE ur.id_user_account=p.id_user_account AND r.code_role='doctor') AS doctor
				FROM professional p LEFT JOIN user_account u USING (id_institution,id_user_account)
				WHERE p.id_institution=? AND p.id_professional=?
				""", (fila, numero) -> new Vinculo(fila.getObject(1, UUID.class), fila.getString(2),
				fila.getString(3), fila.getString(4), fila.getBoolean(5)), institucion, profesional)
				.stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public UUID crearCuenta(UUID institucion, UUID profesional, String correo, String hash) {
		if (Boolean.TRUE.equals(consultas.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM user_account WHERE id_institution=? AND lower(email_user_account)=lower(?))
				""", Boolean.class, institucion, correo))) {
			throw CatalogoException.conflicto("correo", "Ya existe una cuenta con ese correo en la institución. Usa un correo diferente.");
		}
		UUID cuenta = UUID.randomUUID();
		consultas.update("""
				INSERT INTO user_account(id_user_account,id_institution,email_user_account,password_hash_user_account,status_user_account)
				VALUES (?,?,?,?,'active')
				""", cuenta, institucion, correo, hash);
		consultas.update("UPDATE professional SET id_user_account=? WHERE id_institution=? AND id_professional=?",
				cuenta, institucion, profesional);
		return cuenta;
	}

	public void cambiarRol(UUID cuenta, boolean habilitado) {
		if (!habilitado) {
			consultas.update("""
					DELETE FROM user_role WHERE id_user_account=? AND id_role IN (SELECT id_role FROM role WHERE code_role='doctor')
					""", cuenta);
			return;
		}
		consultas.execute("LOCK TABLE role IN SHARE ROW EXCLUSIVE MODE");
		Integer rol = consultas.query("SELECT id_role FROM role WHERE code_role='doctor'", (fila, numero) -> fila.getInt(1))
				.stream().findFirst().orElse(null);
		if (rol == null) {
			Integer mayor = consultas.queryForObject("SELECT coalesce(max(id_role),0) FROM role", Integer.class);
			if (mayor == null || mayor >= Short.MAX_VALUE) throw CatalogoException.conflicto("rol", "No se pudo reservar el rol de doctor.");
			rol = mayor + 1;
			consultas.update("INSERT INTO role(id_role,code_role,description_role) VALUES (?,'doctor','Autoprogramación del profesional vinculado')", rol);
		}
		consultas.update("INSERT INTO user_role(id_user_account,id_role) VALUES (?,?) ON CONFLICT DO NOTHING", cuenta, rol);
	}

	public void auditar(UUID institucion, UUID actor, UUID profesional, boolean habilitado) {
		consultas.update("""
				INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
				entity_id_audit_event,action_audit_event,after_audit_event)
				VALUES (?,?,?,'professional',?,'doctor_access',jsonb_build_object('habilitado',?::boolean))
				""", UUID.randomUUID(), institucion, actor, profesional, habilitado);
	}
}
