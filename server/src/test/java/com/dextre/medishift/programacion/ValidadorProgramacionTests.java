package com.dextre.medishift.programacion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorProgramacionTests {

	@Test
	void validaYNormalizaObservacionSinAceptarCamposDeInstitucionOEstado() {
		Map<String, Object> datos = turno();
		datos.put("observacion", "  Control   de sala  ");
		assertEquals("Control de sala", ValidadorProgramacion.validarTurno(datos).observacion());
		datos.put("idInstitucion", UUID.randomUUID().toString());
		assertTrue(assertThrows(CatalogoException.class, () -> ValidadorProgramacion.validarTurno(datos))
				.obtenerErrores().containsKey("idInstitucion"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "08:00", "07:59", "00:00" })
	void rechazaIntervalosVaciosInvertidosYNocturnos(String fin) {
		Map<String, Object> datos = turno();
		datos.put("horaFin", fin);
		assertTrue(assertThrows(CatalogoException.class, () -> ValidadorProgramacion.validarTurno(datos))
				.obtenerErrores().containsKey("horaFin"));
	}

	@Test
	void rechazaTiposIncorrectosIdsCortosYTextoConControles() {
		Map<String, Object> datos = turno();
		datos.put("idProfesional", "1-1-1-1-1");
		datos.put("fecha", 20261005);
		datos.put("observacion", "Sala\nsegunda");
		Map<String, String> errores = assertThrows(CatalogoException.class,
				() -> ValidadorProgramacion.validarTurno(datos)).obtenerErrores();
		assertTrue(errores.keySet().containsAll(java.util.Set.of("idProfesional", "fecha", "observacion")));
	}

	@Test
	void permite180CaracteresUnicodeYRechaza181() {
		Map<String, Object> datos = turno();
		datos.put("observacion", "😀".repeat(180));
		assertEquals(180, ValidadorProgramacion.validarTurno(datos).observacion().codePointCount(0, 360));
		datos.put("observacion", "😀".repeat(181));
		assertThrows(CatalogoException.class, () -> ValidadorProgramacion.validarTurno(datos));
	}

	@Test
	void accionesSemanalesExigenLunesIdYRevisionCompleta() {
		Map<String, Object> datos = new HashMap<>(Map.of("fechaInicio", "2026-10-05",
				"idHorario", UUID.randomUUID().toString(), "revision", "a".repeat(64)));
		assertEquals("2026-10-05", ValidadorProgramacion.validarSemana(datos).fechaInicio().toString());
		datos.put("fechaInicio", "2026-10-06");
		assertThrows(CatalogoException.class, () -> ValidadorProgramacion.validarSemana(datos));
		assertThrows(CatalogoException.class, () -> ValidadorProgramacion.validarInicio("9999-12-27"));
	}

	@Test
	void revisionRequiereComillasYDetectaSolicitudAntigua() {
		CatalogoException falta = assertThrows(CatalogoException.class,
				() -> RevisionesProgramacion.leerCondicion(null));
		assertEquals(HttpStatus.PRECONDITION_REQUIRED, falta.obtenerEstado());
		assertEquals("a".repeat(64), RevisionesProgramacion.leerCondicion("\"" + "a".repeat(64) + "\""));
		assertThrows(CatalogoException.class, () -> RevisionesProgramacion.leerCondicion("W/\"" + "a".repeat(64) + "\""));
		assertThrows(CatalogoException.class, () -> RevisionesProgramacion.comprobar("a".repeat(64), "b".repeat(64)));
	}

	@Test
	void resumenNoConfundeConcatenacionesYEsEstable() {
		assertNotEquals(RevisionesProgramacion.resumir("ab", "c"), RevisionesProgramacion.resumir("a", "bc"));
		assertEquals(RevisionesProgramacion.resumir("datos", 1), RevisionesProgramacion.resumir("datos", 1));
	}

	private Map<String, Object> turno() {
		return new HashMap<>(Map.of("idProfesional", UUID.randomUUID().toString(),
				"idConsultorio", UUID.randomUUID().toString(), "idEspecialidad", UUID.randomUUID().toString(),
				"fecha", "2026-10-05", "horaInicio", "08:00", "horaFin", "14:00"));
	}
}
