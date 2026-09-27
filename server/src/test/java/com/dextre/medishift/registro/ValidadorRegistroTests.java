package com.dextre.medishift.registro;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorRegistroTests {

	@Test
	void normalizaComoJavascriptSinCambiarCodigoNiContrasena() {
		SolicitudRegistro solicitud = new SolicitudRegistro("\u00a0\ufeffClinica-Lima\u3000",
				"\u2002Clínica Lima\u202f", "America/Lima", "\ufeff CUENTA@Institucion.PE \u00a0", " clave ");
		DatosRegistro datos = ValidadorRegistro.validar(solicitud);

		assertEquals("Clinica-Lima", datos.codigoInstitucion());
		assertEquals("Clínica Lima", datos.nombreInstitucion());
		assertEquals("cuenta@institucion.pe", datos.correo());
		assertEquals(" clave ", datos.contrasena());
		assertFalse(solicitud.toString().contains("clave"));
		assertFalse(datos.toString().contains("clave"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "America/Lima", "America/Bogota", "America/Santiago", "America/La_Paz",
			"America/Mexico_City", "America/Argentina/Buenos_Aires" })
	void admiteLasSeisZonasDelCliente(String zona) {
		assertEquals(zona, ValidadorRegistro.validar(solicitud("zonaHoraria", zona)).zonaHoraria());
	}

	@ParameterizedTest
	@ValueSource(strings = { "UTC", "America/Desconocida", " America/Lima ", "" })
	void rechazaZonasAjenasOAlteradas(String zona) {
		assertCampoInvalido("zonaHoraria", zona);
	}

	@ParameterizedTest
	@ValueSource(strings = { "persona@localhost", "persona+turnos@institucion.pe", "PERSONA@INSTITUCION.PE" })
	void admiteFormatoCorreoDelCliente(String correo) {
		ValidadorRegistro.validar(solicitud("correo", correo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "persona", "@institucion.pe", "persona@", "per sona@institucion.pe",
			"persona@@institucion.pe", "persona@-dominio.pe", "persona@dominio-.pe", "persona@dominio..pe" })
	void rechazaCorreosIncorrectos(String correo) {
		assertCampoInvalido("correo", correo);
	}

	@ParameterizedTest
	@MethodSource("camposYLimites")
	void cuentaCodepointsEnLimites(String campo, int limite) {
		String texto = "😀".repeat(limite);
		DatosRegistro datos = ValidadorRegistro.validar(solicitud(campo, texto));
		assertEquals(texto, campo.equals("codigoInstitucion") ? datos.codigoInstitucion()
				: campo.equals("nombreInstitucion") ? datos.nombreInstitucion() : datos.contrasena());
		assertCampoInvalido(campo, texto + "😀");
	}

	static Stream<Arguments> camposYLimites() {
		return Stream.of(Arguments.of("codigoInstitucion", 32), Arguments.of("nombreInstitucion", 140),
				Arguments.of("contrasena", 1024));
	}

	@Test
	void respetaLimitesDeCorreoYZona() {
		String dominio = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
		String correo = "a".repeat(64) + "@" + dominio;
		assertEquals(254, correo.length());
		assertEquals(correo, ValidadorRegistro.validar(solicitud("correo", correo)).correo());
		assertCampoInvalido("correo", correo + "e");
		assertCampoInvalido("zonaHoraria", "a".repeat(65));
	}

	@ParameterizedTest
	@ValueSource(strings = { "codigoInstitucion", "nombreInstitucion", "zonaHoraria", "correo", "contrasena" })
	void rechazaTiposNoTextualesYNulUnicodeInvalido(String campo) {
		for (Object valor : new Object[] { 123, true, Map.of("valor", "texto"), "texto\0", "\ud800", "\udc00" }) {
			assertCampoInvalido(campo, valor);
		}
		assertCampoInvalido(campo, null);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "\t\r\n", "\u00a0\ufeff\u3000" })
	void rechazaContrasenasBlancasSinRecortarlas(String contrasena) {
		assertCampoInvalido("contrasena", contrasena);
	}

	@Test
	void rechazaSolicitudNulaYAcumulaErrores() {
		assertThrows(ValidacionRegistroException.class, () -> ValidadorRegistro.validar(null));
		ValidacionRegistroException error = assertThrows(ValidacionRegistroException.class,
				() -> ValidadorRegistro.validar(new SolicitudRegistro(null, null, null, null, null)));
		assertEquals(5, error.obtenerErrores().size());
	}

	private void assertCampoInvalido(String campo, Object valor) {
		ValidacionRegistroException error = assertThrows(ValidacionRegistroException.class,
				() -> ValidadorRegistro.validar(solicitud(campo, valor)));
		assertTrue(error.obtenerErrores().containsKey(campo));
	}

	private SolicitudRegistro solicitud(String campo, Object valor) {
		Map<String, Object> datos = new HashMap<>();
		datos.put("codigoInstitucion", "Clinica-Lima");
		datos.put("nombreInstitucion", "Clínica Lima");
		datos.put("zonaHoraria", "America/Lima");
		datos.put("correo", "cuenta@institucion.pe");
		datos.put("contrasena", "clave-de-prueba");
		datos.put(campo, valor);
		return new SolicitudRegistro(datos.get("codigoInstitucion"), datos.get("nombreInstitucion"),
				datos.get("zonaHoraria"), datos.get("correo"), datos.get("contrasena"));
	}

}
