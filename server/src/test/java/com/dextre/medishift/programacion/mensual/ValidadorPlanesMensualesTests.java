package com.dextre.medishift.programacion.mensual;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dextre.medishift.catalogos.CatalogoException;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorPlanesMensualesTests {

	@Test
	void unidadNormalizaEspaciosSinInventarJerarquiaNiCambiarCodigo() {
		var unidad = ValidadorPlanesMensuales.unidad(Map.of("codigo", "  Med-01 ",
				"nombre", "  Medicina\u00a0\u00a0general ", "tipo", "Servicio"));
		assertEquals("Med-01", unidad.codigo());
		assertEquals("Medicina general", unidad.nombre());
		assertEquals("Servicio", unidad.tipo());
	}

	@Test
	void cuotaActorInstitucionEstadoYVersionNoPuedenInyectarse() {
		var entrada = new HashMap<String, Object>(Map.of("idUnidad", UUID.randomUUID().toString(), "periodo", "2028-02"));
		for (String campo : new String[] { "idInstitucion", "idCreador", "estado", "version", "cuota", "revision" }) {
			entrada.put(campo, "no permitido");
		}
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.plan(entrada));
		assertEquals(6, fallo.obtenerErrores().size());
		assertTrue(fallo.obtenerErrores().containsKey("idInstitucion"));
	}

	@Test
	void camposFaltantesSeInformanSinGuardarCabeceraIncompleta() {
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.plan(Map.of()));
		assertEquals(2, fallo.obtenerErrores().size());
		assertTrue(fallo.obtenerErrores().containsKey("idUnidad"));
		assertTrue(fallo.obtenerErrores().containsKey("periodo"));
		assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.unidad(null));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "2026-1", "0000-01", "10000-01", "2026-00", "2026-13", " 2026-01", "2026-01-01" })
	void periodoExigeMesCanonico(String valor) {
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.periodo(valor));
		assertTrue(fallo.obtenerErrores().containsKey("periodo"));
	}

	@Test
	void aceptaExtremosYFebreroBisiesto() {
		assertEquals(29, ValidadorPlanesMensuales.periodo("2028-02").cantidadDias());
		assertEquals(1, ValidadorPlanesMensuales.periodo("0001-01").anio());
		assertEquals(9999, ValidadorPlanesMensuales.periodo("9999-12").anio());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "1-1-1-1-1", "no-uuid", "00000000-0000-0000-0000-00000000000x" })
	void rechazaIdentificadoresAbreviadosOMalformados(String valor) {
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.identificador(valor, "idUnidad"));
		assertTrue(fallo.obtenerErrores().containsKey("idUnidad"));
	}

	@Test
	void unidadCuentaPuntosUnicodeYRechazaControlesOSurrogados() {
		assertEquals(32, ValidadorPlanesMensuales.unidad(Map.of("codigo", "😀".repeat(32), "nombre", "Unidad", "tipo", "Departamento"))
				.codigo().codePointCount(0, 64));
		for (String nombre : new String[] { "A\0B", "\ud800", "Nombre\ncontrol", "a".repeat(141), "\u2003" }) {
			assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.unidad(Map.of("codigo", "U01", "nombre", nombre, "tipo", "Servicio")));
		}
		assertThrows(CatalogoException.class, () -> ValidadorPlanesMensuales.unidad(Map.of("codigo", "U01", "nombre", "Unidad", "tipo", "Especialidad")));
	}
}
