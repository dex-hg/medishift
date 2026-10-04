package com.dextre.medishift.disponibilidad;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorDisponibilidadesTests {

	@Test
	void conservaVentanaMayorQueJornadaYNormalizaObservacionUnicode() {
		Map<String, Object> solicitud = solicitudValida();
		solicitud.put("observacion", "  Atención\u00a0 con   observación  ");
		var validada = ValidadorDisponibilidades.validar(solicitud);
		assertEquals("08:00", validada.horaInicio().toString());
		assertEquals("16:00", validada.horaFin().toString());
		assertEquals("Atención con observación", validada.observacion());
		solicitud.remove("observacion");
		assertEquals("", ValidadorDisponibilidades.validar(solicitud).observacion());
	}

	@ParameterizedTest
	@MethodSource("datosInvalidos")
	void rechazaTiposFormatosCalendarioYTextoFueraDelContrato(String campo, Object valor) {
		Map<String, Object> solicitud = solicitudValida();
		solicitud.put(campo, valor);
		CatalogoException excepcion = assertThrows(CatalogoException.class,
				() -> ValidadorDisponibilidades.validar(solicitud));
		assertEquals(400, excepcion.obtenerEstado().value());
		assertTrue(excepcion.obtenerErrores().containsKey(campo), excepcion.obtenerErrores().toString());
	}

	static Stream<Arguments> datosInvalidos() {
		return Stream.of(
				Arguments.of("idProfesional", "1-1-1-1-1"), Arguments.of("idProfesional", 123),
				Arguments.of("idProfesional", null), Arguments.of("idProfesional", "no-es-un-uuid"),
				Arguments.of("diaSemana", "1"), Arguments.of("diaSemana", 1.0),
				Arguments.of("diaSemana", true), Arguments.of("diaSemana", 0), Arguments.of("diaSemana", 8),
				Arguments.of("diaSemana", new BigInteger("99999999999999999999999")),
				Arguments.of("horaInicio", "8:00"), Arguments.of("horaInicio", "08:00:00"),
				Arguments.of("horaInicio", " 08:00"), Arguments.of("horaInicio", "24:00"),
				Arguments.of("horaInicio", "08:60"), Arguments.of("horaInicio", 800),
				Arguments.of("horaFin", "08:00"), Arguments.of("horaFin", "07:59"),
				Arguments.of("fechaInicio", "2027-02-29"), Arguments.of("fechaInicio", "2027-02-30"),
				Arguments.of("fechaInicio", "2027-1-04"), Arguments.of("fechaInicio", "0000-01-04"),
				Arguments.of("fechaInicio", "2027-01-04T00:00:00"), Arguments.of("fechaInicio", null),
				Arguments.of("fechaFin", "2027-01-03"), Arguments.of("estado", "active"),
				Arguments.of("estado", null), Arguments.of("observacion", null),
				Arguments.of("observacion", "a".repeat(241)), Arguments.of("observacion", "texto\ncontrol"),
				Arguments.of("observacion", "texto\u0000control"), Arguments.of("observacion", "\ud83d"),
				Arguments.of("observacion", "\ude00"));
	}

	@Test
	void admiteFechaBisiestaRealYCuentaCaracteresUnicodeCompletos() {
		Map<String, Object> solicitud = solicitudValida();
		solicitud.put("diaSemana", 2);
		solicitud.put("fechaInicio", "2028-02-29");
		solicitud.put("fechaFin", "2028-02-29");
		solicitud.put("observacion", "😀".repeat(240));
		assertEquals(LocalDate.of(2028, 2, 29), ValidadorDisponibilidades.validar(solicitud).fechaInicio());
	}

	@Test
	void unaVigenciaDeUnDiaEsValidaSoloSiCoincideConDiaSemanal() {
		Map<String, Object> solicitud = solicitudValida();
		solicitud.put("fechaFin", "2027-01-04");
		assertEquals(1, ValidadorDisponibilidades.validar(solicitud).diaSemana());
		solicitud.put("diaSemana", 2);
		CatalogoException excepcion = assertThrows(CatalogoException.class,
				() -> ValidadorDisponibilidades.validar(solicitud));
		assertTrue(excepcion.obtenerErrores().containsKey("fechaFin"));
	}

	@Test
	void rechazaCamposDeInstitucionRevisionYUnJsonNulo() {
		Map<String, Object> solicitud = solicitudValida();
		solicitud.put("idInstitucion", UUID.randomUUID().toString());
		solicitud.put("revision", "obsoleta");
		CatalogoException excepcion = assertThrows(CatalogoException.class,
				() -> ValidadorDisponibilidades.validar(solicitud));
		assertEquals(2, excepcion.obtenerErrores().size());
		assertThrows(CatalogoException.class, () -> ValidadorDisponibilidades.validar(null));
	}

	@Test
	void calculaOcurrenciasSinRecorrerTodaLaVigenciaYRespetaExtremosInclusivos() {
		LocalDate martes = LocalDate.of(2027, 1, 5);
		assertFalse(ValidadorDisponibilidades.tieneOcurrencia(martes, martes.plusDays(5), 1));
		assertTrue(ValidadorDisponibilidades.tieneOcurrencia(martes, martes.plusDays(6), 1));
		assertTrue(ValidadorDisponibilidades.tieneOcurrencia(martes, martes, 2));
		assertFalse(ValidadorDisponibilidades.tieneOcurrencia(martes, martes.minusDays(1), 2));
		assertTrue(ValidadorDisponibilidades.tieneOcurrencia(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), 7));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void exigeRevisionParaMutaciones(String cabecera) {
		CatalogoException excepcion = assertThrows(CatalogoException.class,
				() -> RevisionDisponibilidades.validarCabecera(cabecera));
		assertEquals(428, excepcion.obtenerEstado().value());
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "abc", "W/\"abc\"", "\"abc\"", "null" })
	void rechazaRevisionesDebilesMultiplesOIncompletas(String cabecera) {
		assertEquals(400, assertThrows(CatalogoException.class,
				() -> RevisionDisponibilidades.validarCabecera(cabecera)).obtenerEstado().value());
	}

	@Test
	void revisionDetectaCambioGuardadoSinInvalidarseSoloPorNombreVisible() {
		UUID id = UUID.randomUUID();
		UUID profesional = UUID.randomUUID();
		Disponibilidad actual = disponibilidad(id, profesional, "Ana Pérez", "Observación");
		String revision = RevisionDisponibilidades.calcular(actual);
		assertTrue(revision.matches("[a-f0-9]{64}"));
		assertEquals(revision, RevisionDisponibilidades.calcular(disponibilidad(id, profesional, "Nombre nuevo", "Observación")));
		assertNotEquals(revision, RevisionDisponibilidades.calcular(disponibilidad(id, profesional, "Ana Pérez", "Cambio")));
		assertEquals(revision, RevisionDisponibilidades.validarCabecera('"' + revision + '"'));
	}

	private Disponibilidad disponibilidad(UUID id, UUID profesional, String nombre, String observacion) {
		return new Disponibilidad(id, profesional, nombre, 1, "08:00", "16:00", "2027-01-04", "2027-01-31",
				"Activo", observacion, "");
	}

	private Map<String, Object> solicitudValida() {
		return new LinkedHashMap<>(Map.of("idProfesional", UUID.randomUUID().toString(), "diaSemana", 1,
				"horaInicio", "08:00", "horaFin", "16:00", "fechaInicio", "2027-01-04", "fechaFin", "2027-01-31",
				"estado", "Activo", "observacion", ""));
	}

}
