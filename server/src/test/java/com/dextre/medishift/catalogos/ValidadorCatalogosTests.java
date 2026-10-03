package com.dextre.medishift.catalogos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorCatalogosTests {

	@Test
	void normalizaTextoUnicodeCorreoYColegiaturaSinCambiarEstado() {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put("nombres", "  Mari\u0301a\u00a0 del  Carmen ");
		solicitud.put("colegiatura", " cmp-123 ");
		solicitud.put("correo", " MEDICA@Institucion.PE ");
		solicitud.remove("telefono");
		DatosCatalogos.Profesional datos = ValidadorCatalogos.validarProfesional(solicitud);
		assertEquals("María del Carmen", datos.nombres());
		assertEquals("CMP-123", datos.colegiatura());
		assertEquals("medica@institucion.pe", datos.correo());
		assertEquals("", datos.telefono());
		assertEquals("Activo", datos.estado());
	}

	@ParameterizedTest
	@MethodSource("camposProfesional")
	void rechazaTextoObligatorioVacioOSustituidoPorNumero(String campo) {
		Map<String, Object> solicitud = profesionalValido();
		for (Object valor : List.of(" \u00a0 ", 123)) {
			solicitud.put(campo, valor);
			CatalogoException error = assertThrows(CatalogoException.class,
					() -> ValidadorCatalogos.validarProfesional(solicitud));
			assertEquals(HttpStatus.BAD_REQUEST, error.obtenerEstado());
			assertTrue(error.obtenerErrores().containsKey(campo));
		}
	}

	@ParameterizedTest
	@MethodSource("longitudesProfesional")
	void respetaLimitesDeColumnasSinTruncar(String campo, int longitud) {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put(campo, "x".repeat(longitud + 1));
		CatalogoException error = assertThrows(CatalogoException.class,
				() -> ValidadorCatalogos.validarProfesional(solicitud));
		assertTrue(error.obtenerErrores().containsKey(campo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "correo-sin-arroba", "persona@", "persona@institucion", "persona\n@institucion.pe" })
	void rechazaCorreosMalformados(String correo) {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put("correo", correo);
		assertTrue(assertThrows(CatalogoException.class,
				() -> ValidadorCatalogos.validarProfesional(solicitud)).obtenerErrores().containsKey("correo"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "123456", "+1234567890123456", "999ABC777", "999/888/777" })
	void rechazaTelefonosConDigitosInsuficientesExcesivosOTexto(String telefono) {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put("telefono", telefono);
		assertTrue(assertThrows(CatalogoException.class,
				() -> ValidadorCatalogos.validarProfesional(solicitud)).obtenerErrores().containsKey("telefono"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "active", "Suspendido", "", "ACTIVO" })
	void validaEstadosDelContrato(String estado) {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put("estado", estado);
		assertTrue(assertThrows(CatalogoException.class,
				() -> ValidadorCatalogos.validarProfesional(solicitud)).obtenerErrores().containsKey("estado"));
	}

	@Test
	void rechazaAsignacionDeInstitucionIdYCadenasConUnicodeInvalido() {
		Map<String, Object> solicitud = profesionalValido();
		solicitud.put("idInstitucion", UUID.randomUUID().toString());
		solicitud.put("id", UUID.randomUUID().toString());
		solicitud.put("nombres", "Nombre\u0000");
		solicitud.put("apellidos", "Apellido\ud800");
		Map<String, String> errores = assertThrows(CatalogoException.class,
				() -> ValidadorCatalogos.validarProfesional(solicitud)).obtenerErrores();
		assertTrue(errores.keySet().containsAll(List.of("idInstitucion", "id", "nombres", "apellidos")));
	}

	@Test
	void consultorioGeneralAdmiteEspecialidadVaciaYRechazaTiposIncorrectos() {
		Map<String, Object> solicitud = new LinkedHashMap<>(Map.of("codigo", " c-001 ",
				"nombre", "Consultorio", "ubicacion", "Piso 1", "estado", "Inactivo", "especialidad", ""));
		DatosCatalogos.Consultorio datos = ValidadorCatalogos.validarConsultorio(solicitud);
		assertEquals("C-001", datos.codigo());
		assertEquals("", datos.especialidad());
		for (String campo : List.of("codigo", "nombre", "ubicacion", "especialidad", "estado")) {
			Map<String, Object> invalida = new LinkedHashMap<>(solicitud);
			invalida.put(campo, List.of("valor"));
			assertTrue(assertThrows(CatalogoException.class,
						() -> ValidadorCatalogos.validarConsultorio(invalida)).obtenerErrores().containsKey(campo));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "123", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000Z", "" })
	void rechazaUuidMalformadosYAbreviados(String id) {
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(CatalogoException.class, () -> ValidadorCatalogos.validarId(id)).obtenerEstado());
	}

	@Test
	void aceptaUuidCompletoYRechazaSolicitudNula() {
		UUID id = UUID.randomUUID();
		assertEquals(id, ValidadorCatalogos.validarId(id.toString().toUpperCase(Locale.ROOT)));
		assertThrows(CatalogoException.class, () -> ValidadorCatalogos.validarProfesional(null));
		assertThrows(CatalogoException.class, () -> ValidadorCatalogos.validarConsultorio(null));
	}

	private static Stream<String> camposProfesional() {
		return Stream.of("nombres", "apellidos", "colegiatura", "correo", "categoria", "estado", "especialidad");
	}

	private static Stream<Arguments> longitudesProfesional() {
		return Stream.of(Arguments.of("nombres", 80), Arguments.of("apellidos", 100),
				Arguments.of("colegiatura", 32), Arguments.of("correo", 254), Arguments.of("telefono", 20),
				Arguments.of("categoria", 40), Arguments.of("especialidad", 100));
	}

	static Map<String, Object> profesionalValido() {
		return new LinkedHashMap<>(Map.of("nombres", "Ana", "apellidos", "Pérez", "colegiatura", "CMP-123",
				"correo", "ana@institucion.pe", "telefono", "999888777", "categoria", "Médico",
				"estado", "Activo", "especialidad", "Medicina general"));
	}
}
