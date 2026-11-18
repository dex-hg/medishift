package com.dextre.medishift.configuracion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.zaxxer.hikari.HikariDataSource;

@Component
public class VerificadorConexionBaseDatos implements ApplicationRunner {

	private static final Logger REGISTRO = LoggerFactory.getLogger(VerificadorConexionBaseDatos.class);
	private static final int TIEMPO_VALIDACION_SEGUNDOS = 5;
	private static final Pattern URL_TLS = Pattern.compile(
			"jdbc:postgresql://[a-zA-Z0-9.-]+:[0-9]{1,5}/postgres\\?sslmode=verify-full");

	private final DataSource origenDatos;
	private final boolean verificarSupabase;

	public VerificadorConexionBaseDatos(DataSource origenDatos,
			@Value("${MEDISHIFT_BD_CLAVE:}") String claveBaseDatos, Environment entorno) {
		Assert.hasText(claveBaseDatos,
				"Configura MEDISHIFT_BD_CLAVE en .env o en el entorno de ejecución.");
		this.origenDatos = origenDatos;
		this.verificarSupabase = entorno.matchesProfiles("supabase");
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (verificarSupabase) {
			Assert.state(origenDatos instanceof HikariDataSource pool && pool.getJdbcUrl() != null
					&& URL_TLS.matcher(pool.getJdbcUrl()).matches(),
					"El perfil supabase requiere una URL JDBC sin credenciales y con sslmode=verify-full.");
		}
		try (Connection conexion = origenDatos.getConnection()) {
			if (!conexion.isValid(TIEMPO_VALIDACION_SEGUNDOS)) {
				throw new IllegalStateException("PostgreSQL devolvió una conexión no válida.");
			}
			if (verificarSupabase) {
				verificarAccesoSupabase(conexion);
			}
		} catch (SQLException excepcion) {
			throw new IllegalStateException(
					"No se pudo verificar la conexión con PostgreSQL. "
							+ "Revisa la conexión y las credenciales del entorno.",
					excepcion);
		}

		REGISTRO.info("Conexión con PostgreSQL verificada correctamente.");
	}

	private void verificarAccesoSupabase(Connection conexion) throws SQLException {
		try (var consulta = conexion.createStatement(); var filas = consulta.executeQuery("""
				SELECT session_user = 'medishift_app' AND session_user = current_user
				       AND current_schema() = 'medishift'
				       AND NOT has_schema_privilege(to_regnamespace('medishift'), 'CREATE')
				       AND has_table_privilege(to_regclass('medishift.paciente'), 'SELECT')
				       AND has_table_privilege(to_regclass('medishift.etiqueta_consultorio_programable'), 'SELECT')
				""")) {
			Assert.state(filas.next() && filas.getBoolean(1),
					"Instala el esquema privado y conecta el perfil supabase con el rol medishift_app.");
		}
	}

}
