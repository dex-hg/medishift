package com.dextre.medishift.registro;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.dextre.medishift.acceso.RepositorioAccesoMensual;
import com.dextre.medishift.sesion.RepositorioSesion;
import com.dextre.medishift.sesion.ServicioSesion;

/** Registros confirmados en un esquema privado; preserva la auditoría inmutable de public. */
public abstract class SoporteCuentasPostgresql {

	@Autowired private DataSource origen;
	@Autowired protected PasswordEncoder codificador;
	protected JdbcTemplate consultas;
	protected PlatformTransactionManager gestor;
	protected RepositorioAccesoMensual accesos;
	protected ServicioRegistro registro;
	protected ServicioSesion sesiones;
	private Connection conexion;
	private String esquema;

	@BeforeEach
	void prepararCuentasAisladas() throws Exception {
		conexion = origen.getConnection();
		try {
			conexion.setAutoCommit(true);
			esquema = "prueba_cuentas_" + UUID.randomUUID().toString().replace("-", "");
			var fuentePrivada = new SingleConnectionDataSource(conexion, true);
			consultas = new JdbcTemplate(fuentePrivada);
			consultas.execute("CREATE SCHEMA " + esquema);
			consultas.execute("SET search_path TO " + esquema + ", public");
			String base = Files.readString(Path.of("../db/medishift_postgresql.sql"))
					.replaceAll("(?m)^(BEGIN|COMMIT);\\s*$", "")
					.replace("CREATE EXTENSION IF NOT EXISTS btree_gist;", "");
			consultas.execute(base);
			gestor = new DataSourceTransactionManager(fuentePrivada);
			accesos = new RepositorioAccesoMensual(consultas);
			registro = new ServicioRegistro(new RepositorioRegistro(consultas), accesos, codificador, gestor);
			sesiones = new ServicioSesion(new RepositorioSesion(consultas), codificador);
		} catch (Exception | AssertionError fallo) {
			cerrarCuentasAisladas();
			throw fallo;
		}
	}

	@AfterEach
	void cerrarCuentasAisladas() throws SQLException {
		if (conexion == null) { return; }
		try {
			if (!conexion.getAutoCommit()) { conexion.rollback(); conexion.setAutoCommit(true); }
			try (var instruccion = conexion.createStatement()) {
				instruccion.execute("RESET search_path");
				if (esquema != null && esquema.matches("prueba_cuentas_[0-9a-f]{32}")) {
					instruccion.execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
				}
			}
		} finally {
			conexion.close();
			conexion = null;
		}
	}
}
