package com.dextre.medishift.registro;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioRegistro {

	private final JdbcTemplate consultas;

	public RepositorioRegistro(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public void insertarInstitucion(UUID idInstitucion, DatosRegistro datos) {
		int insertadas = consultas.update("""
				INSERT INTO institution
				    (id_institution, code_institution, name_institution,
				     time_zone_institution, status_institution)
				VALUES (?, ?, ?, ?, 'active')
				ON CONFLICT (code_institution) DO NOTHING
				""", idInstitucion, datos.codigoInstitucion(), datos.nombreInstitucion(), datos.zonaHoraria());
		if (insertadas == 0) {
			throw new InstitucionDuplicadaException();
		}
	}

	public void insertarCuenta(UUID idCuenta, UUID idInstitucion, String correo, String hashContrasena) {
		consultas.update("""
				INSERT INTO user_account
				    (id_user_account, id_institution, email_user_account,
				     password_hash_user_account, status_user_account)
				VALUES (?, ?, ?, ?, 'active')
				""", idCuenta, idInstitucion, correo, hashContrasena);
	}

}
