package com.dextre.medishift.acceso;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;

@Repository
public class RepositorioAccesoMensual {

	private final JdbcTemplate consultas;

	public RepositorioAccesoMensual(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public boolean administradorActivo(UUID institucion, UUID cuenta) {
		return Boolean.TRUE.equals(consultas.queryForObject("""
				SELECT EXISTS (
				 SELECT 1 FROM user_account u
				 JOIN institution i ON i.id_institution = u.id_institution
				 JOIN user_role ur ON ur.id_user_account = u.id_user_account
				 JOIN role r ON r.id_role = ur.id_role
				 WHERE u.id_institution = ? AND u.id_user_account = ?
				   AND u.status_user_account = 'active' AND i.status_institution = 'active'
				   AND r.code_role = 'administrador')
				""", Boolean.class, institucion, cuenta));
	}

	public boolean cuentaActiva(UUID institucion, UUID cuenta) {
		return Boolean.TRUE.equals(consultas.queryForObject("""
				SELECT EXISTS (
				 SELECT 1 FROM user_account u
				 JOIN institution i ON i.id_institution = u.id_institution
				 WHERE u.id_institution = ? AND u.id_user_account = ?
				   AND u.status_user_account = 'active' AND i.status_institution = 'active')
				""", Boolean.class, institucion, cuenta));
	}

	public boolean existeAdministradorActivo(UUID institucion) {
		return Boolean.TRUE.equals(consultas.queryForObject("""
				SELECT EXISTS (
				 SELECT 1 FROM user_account u
				 JOIN institution i ON i.id_institution = u.id_institution
				 JOIN user_role ur ON ur.id_user_account = u.id_user_account
				 JOIN role r ON r.id_role = ur.id_role
				 WHERE u.id_institution = ? AND u.status_user_account = 'active'
				   AND i.status_institution = 'active' AND r.code_role = 'administrador')
				""", Boolean.class, institucion));
	}

	/** El bloqueo global evita reutilizar un identificador entre instituciones. */
	public int resolverRolAdministrador() {
		consultas.execute("LOCK TABLE role IN SHARE ROW EXCLUSIVE MODE");
		Integer existente = consultas.query("SELECT id_role FROM role WHERE code_role = 'administrador'",
				(fila, numero) -> fila.getInt("id_role")).stream().findFirst().orElse(null);
		if (existente != null) {
			return existente;
		}
		Integer mayor = consultas.queryForObject("SELECT coalesce(max(id_role), 0) FROM role", Integer.class);
		if (mayor == null || mayor < 0 || mayor >= Short.MAX_VALUE) {
			throw CatalogoException.conflicto("rol", "No se pudo reservar el rol de administrador.");
		}
		int identificador = mayor + 1;
		consultas.update("""
				INSERT INTO role (id_role, code_role, description_role)
				VALUES (?, 'administrador', 'Administración institucional')
				""", identificador);
		return identificador;
	}

	public void asignarAdministrador(UUID cuenta, int rol) {
		consultas.update("INSERT INTO user_role (id_user_account, id_role) VALUES (?, ?)", cuenta, rol);
	}

	public void registrarInicializacion(UUID institucion, UUID cuenta) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event, id_institution, id_actor_user_account,
				 entity_name_audit_event, entity_id_audit_event, action_audit_event, after_audit_event)
				VALUES (?, ?, NULL, 'user_account', ?, 'bootstrap_admin',
				 jsonb_build_object('institucion', ?::text, 'cuenta', ?::text,
				                    'rol', 'administrador', 'origen', 'local'))
				""", UUID.randomUUID(), institucion, cuenta, institucion, cuenta);
	}

	public void registrarAltaInstitucion(UUID institucion, UUID cuenta) {
		consultas.update("""
				INSERT INTO audit_event (id_audit_event, id_institution, id_actor_user_account,
				 entity_name_audit_event, entity_id_audit_event, action_audit_event, after_audit_event)
				VALUES (?, ?, ?, 'user_account', ?, 'register_admin',
				 jsonb_build_object('rol', 'administrador', 'origen', 'registro_institucion'))
				""", UUID.randomUUID(), institucion, cuenta, cuenta);
	}
}
