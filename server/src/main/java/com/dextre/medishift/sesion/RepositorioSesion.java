package com.dextre.medishift.sesion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioSesion {

	public record CuentaAutenticable(RespuestaSesion perfil, String hashContrasena) {
		@Override
		public String toString() {
			return "CuentaAutenticable[datos omitidos]";
		}
	}

	private final JdbcTemplate consultas;

	public RepositorioSesion(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public Optional<CuentaAutenticable> buscarCredenciales(String codigoInstitucion, String correo) {
		return consultas.query("""
				SELECT u.id_user_account, u.id_institution, i.code_institution,
				       i.name_institution, u.email_user_account, u.password_hash_user_account
				FROM institution i
				JOIN user_account u ON u.id_institution = i.id_institution
				WHERE i.code_institution = ?
				  AND lower(u.email_user_account) = ?
				  AND i.status_institution = 'active'
				  AND u.status_user_account = 'active'
				""", (fila, numero) -> new CuentaAutenticable(leerPerfil(fila),
				fila.getString("password_hash_user_account")), codigoInstitucion, correo)
				.stream().findFirst();
	}

	public Optional<RespuestaSesion> buscarSesion(UUID idInstitucion, UUID idCuenta) {
		return consultas.query("""
				SELECT u.id_user_account, u.id_institution, i.code_institution,
				       i.name_institution, u.email_user_account
				FROM user_account u
				JOIN institution i ON i.id_institution = u.id_institution
				WHERE u.id_institution = ? AND u.id_user_account = ?
				  AND i.status_institution = 'active'
				  AND u.status_user_account = 'active'
				""", (fila, numero) -> leerPerfil(fila), idInstitucion, idCuenta)
				.stream().findFirst();
	}

	private RespuestaSesion leerPerfil(ResultSet fila) throws SQLException {
		return new RespuestaSesion(fila.getObject("id_user_account", UUID.class),
				fila.getObject("id_institution", UUID.class), fila.getString("code_institution"),
				fila.getString("name_institution"),
				fila.getString("email_user_account").toLowerCase(Locale.ROOT));
	}

}
