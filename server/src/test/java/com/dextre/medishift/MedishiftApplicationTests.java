package com.dextre.medishift;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-conexion", matches = "true")
class MedishiftApplicationTests {

	@Autowired
	private DataSource origenDatos;

	@Test
	void contextoCargaConConexionPostgresql() throws SQLException {
		try (Connection conexion = origenDatos.getConnection()) {
			assertTrue(conexion.isValid(5), "La conexión real con PostgreSQL debe ser válida.");
		}
	}

}
