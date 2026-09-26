package com.dextre.medishift.configuracion;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class VerificadorConexionBaseDatos implements ApplicationRunner {

	private static final Logger REGISTRO = LoggerFactory.getLogger(VerificadorConexionBaseDatos.class);
	private static final int TIEMPO_VALIDACION_SEGUNDOS = 5;

	private final DataSource origenDatos;

	public VerificadorConexionBaseDatos(DataSource origenDatos,
			@Value("${MEDISHIFT_BD_CLAVE:}") String claveBaseDatos) {
		Assert.hasText(claveBaseDatos,
				"Configura MEDISHIFT_BD_CLAVE en .env o en el entorno de ejecución.");
		this.origenDatos = origenDatos;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		try (Connection conexion = origenDatos.getConnection()) {
			if (!conexion.isValid(TIEMPO_VALIDACION_SEGUNDOS)) {
				throw new IllegalStateException("PostgreSQL devolvió una conexión no válida.");
			}
		} catch (SQLException excepcion) {
			throw new IllegalStateException(
					"No se pudo verificar la conexión con PostgreSQL. "
							+ "Revisa el servicio, el nombre de la base de datos y las credenciales locales.",
					excepcion);
		}

		REGISTRO.info("Conexión con PostgreSQL verificada correctamente.");
	}

}
