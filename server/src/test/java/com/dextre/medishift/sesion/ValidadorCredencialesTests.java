package com.dextre.medishift.sesion;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dextre.medishift.registro.DatosCredenciales;
import com.dextre.medishift.registro.ValidadorRegistro;
import com.dextre.medishift.registro.ValidacionRegistroException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorCredencialesTests {

	@Test
	void conservaMayusculasDelCodigoYContrasenaExacta() {
		DatosCredenciales datos = ValidadorRegistro.validarCredenciales("\u00a0\ufeffClinica-Lima ",
				" CUENTA@Institucion.PE\u00a0", " Clave 😀 ");
		assertEquals("Clinica-Lima", datos.codigoInstitucion());
		assertEquals("cuenta@institucion.pe", datos.correo());
		assertEquals(" Clave 😀 ", datos.contrasena());
		assertFalse(datos.toString().contains("Clave"));
	}

	@Test
	void aceptaDominioLocalYLimitesPorPuntoUnicode() {
		ValidadorRegistro.validarCredenciales("🩺".repeat(32), "persona@localhost", "🩺".repeat(1024));
		assertInvalido("codigoInstitucion", "🩺".repeat(33), "persona@localhost", "clave");
		assertInvalido("contrasena", "Clinica-Lima", "persona@localhost", "🩺".repeat(1025));
		String correo = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63)
				+ "." + "d".repeat(61);
		assertEquals(254, correo.length());
		ValidadorRegistro.validarCredenciales("Clinica-Lima", correo, "clave");
		assertInvalido("correo", "Clinica-Lima", correo + "e", "clave");
	}

	@Test
	void rechazaTiposNoTextualesVaciosYNulUnicodeInvalido() {
		for (Object valor : new Object[] { null, 123, true, Map.of("dato", "texto"), "",
				"\u00a0\ufeff", "texto\0", "\ud800", "\udc00" }) {
			assertInvalido("codigoInstitucion", valor, "persona@localhost", "clave");
			assertInvalido("correo", "Clinica-Lima", valor, "clave");
			assertInvalido("contrasena", "Clinica-Lima", "persona@localhost", valor);
		}
	}

	@Test
	void rechazaFormatoDeCorreoSinLimitarContrasenaCorta() {
		ValidadorRegistro.validarCredenciales("Clinica-Lima", "PERSONA@LOCALHOST", "a");
		for (String correo : new String[] { "persona", "@institucion.pe", "persona@", "persona@@localhost",
				"persona@-dominio.pe", "per sona@localhost" }) {
			assertInvalido("correo", "Clinica-Lima", correo, "clave");
		}
	}

	private void assertInvalido(String campo, Object codigo, Object correo, Object contrasena) {
		ValidacionRegistroException error = assertThrows(ValidacionRegistroException.class,
				() -> ValidadorRegistro.validarCredenciales(codigo, correo, contrasena));
		assertTrue(error.obtenerErrores().containsKey(campo));
	}

}
