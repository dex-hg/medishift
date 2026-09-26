package com.dextre.medishift.configuracion;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificadorConexionBaseDatosTests {

	@Mock
	private DataSource origenDatos;

	@Mock
	private Connection conexion;

	private VerificadorConexionBaseDatos verificador;

	@BeforeEach
	void prepararVerificador() {
		verificador = new VerificadorConexionBaseDatos(origenDatos, "clave_de_prueba_no_real");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t" })
	void rechazaClaveAusenteOVacia(String claveBaseDatos) {
		assertThrows(IllegalArgumentException.class,
				() -> new VerificadorConexionBaseDatos(origenDatos, claveBaseDatos));

		verifyNoInteractions(origenDatos);
	}

	@Test
	void verificaConexionValidaYLaCierra() throws SQLException {
		when(origenDatos.getConnection()).thenReturn(conexion);
		when(conexion.isValid(5)).thenReturn(true);

		assertDoesNotThrow(() -> verificador.run(new DefaultApplicationArguments()));

		verify(conexion).close();
	}

	@Test
	void rechazaConexionInvalidaYLaCierra() throws SQLException {
		when(origenDatos.getConnection()).thenReturn(conexion);
		when(conexion.isValid(5)).thenReturn(false);

		assertThrows(IllegalStateException.class,
				() -> verificador.run(new DefaultApplicationArguments()));

		verify(conexion).close();
	}

	@Test
	void informaErrorAlObtenerConexion() throws SQLException {
		SQLException errorSql = new SQLException("Conexión rechazada", "08001");
		when(origenDatos.getConnection()).thenThrow(errorSql);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> verificador.run(new DefaultApplicationArguments()));

		assertSame(errorSql, error.getCause());
	}

	@Test
	void cierraConexionSiLaValidacionFalla() throws SQLException {
		when(origenDatos.getConnection()).thenReturn(conexion);
		when(conexion.isValid(5)).thenThrow(new SQLException("Error de validación", "08006"));

		assertThrows(IllegalStateException.class,
				() -> verificador.run(new DefaultApplicationArguments()));

		verify(conexion).close();
	}

}
