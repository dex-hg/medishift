package com.dextre.medishift.configuracion;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

@Configuration
public class ConfiguracionClaves {

	public static final String IDENTIFICADOR_CODIFICACION = "pbkdf2@medishift-v1";

	@Bean
	public PasswordEncoder codificadorContrasenas() {
		PasswordEncoder codificador = new Pbkdf2PasswordEncoder("", 16, 600_000,
				SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
		return new DelegatingPasswordEncoder(IDENTIFICADOR_CODIFICACION,
				Map.of(IDENTIFICADOR_CODIFICACION, codificador));
	}

}
