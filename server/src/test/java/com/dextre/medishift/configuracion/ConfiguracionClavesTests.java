package com.dextre.medishift.configuracion;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionClavesTests {

	@Test
	void produceHashesConSaltAleatorioYPrefijoVersionado() {
		PasswordEncoder codificador = new ConfiguracionClaves().codificadorContrasenas();
		String contrasena = " clave " + "á😀".repeat(45);
		String primerHash = codificador.encode(contrasena);
		String segundoHash = codificador.encode(contrasena);

		assertTrue(primerHash.startsWith("{pbkdf2@medishift-v1}"));
		assertNotEquals(primerHash, segundoHash);
		assertFalse(primerHash.contains(contrasena));
		assertTrue(codificador.matches(contrasena, primerHash));
		assertFalse(codificador.matches(contrasena.trim(), primerHash));
		assertFalse(codificador.matches(contrasena + "distinta", primerHash));
	}

}
