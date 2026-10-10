package com.dextre.medishift.programacion.mensual;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PeriodoMensualTests {

	@ParameterizedTest
	@CsvSource({ "2026, 2, 28", "2028, 2, 29", "2000, 2, 29", "2100, 2, 28",
			"2026, 4, 30", "2026, 10, 31" })
	void representaElMesCompletoSegunSuCalendario(int anio, int mes, int dias) {
		PeriodoMensual periodo = new PeriodoMensual(anio, mes);
		assertEquals(LocalDate.of(anio, mes, 1), periodo.inicio());
		assertEquals(LocalDate.of(anio, mes, dias), periodo.fin());
		assertEquals(periodo.fin().plusDays(1), periodo.finExclusivo());
		assertEquals(dias, periodo.cantidadDias());
	}

	@ParameterizedTest
	@CsvSource({ "0, 1", "-1, 1", "10000, 1", "2026, 0", "2026, -1", "2026, 13" })
	void rechazaComponentesFueraDelContrato(int anio, int mes) {
		assertThrows(IllegalArgumentException.class, () -> new PeriodoMensual(anio, mes));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "2026-1", "26-01", "2026-00", "2026-13", "0000-01", "10000-01",
			"+2026-01", "-2026-01", "2026-01-01", "2026/01", " 2026-01", "2026-01 ",
			"2026-01\n", "２０２６-０１" })
	void rechazaPeriodosInvalidosSinNormalizarLaEntrada(String valor) {
		assertThrows(IllegalArgumentException.class, () -> PeriodoMensual.analizar(valor));
	}

	@Test
	void analizaFormatoExactoYConservaIdentidadPorValor() {
		PeriodoMensual periodo = PeriodoMensual.analizar("2026-10");
		assertEquals(new PeriodoMensual(2026, 10), periodo);
		assertEquals(new PeriodoMensual(2026, 10).hashCode(), periodo.hashCode());
		assertEquals(2026, periodo.anio());
		assertEquals(10, periodo.mes());
		assertNotEquals(new PeriodoMensual(2026, 9), periodo);
	}

	@Test
	void aceptaLosAniosExtremosYConservaElLimiteExclusivoSuperior() {
		PeriodoMensual primero = PeriodoMensual.analizar("0001-01");
		PeriodoMensual ultimo = PeriodoMensual.analizar("9999-12");
		assertEquals(LocalDate.of(1, 1, 1), primero.inicio());
		assertEquals(LocalDate.of(9999, 12, 31), ultimo.fin());
		assertEquals(LocalDate.of(10000, 1, 1), ultimo.finExclusivo());
		assertEquals(Instant.parse("+10000-01-01T00:00:00Z"),
				ultimo.finExclusivoInstantaneo(ZoneOffset.UTC));
	}

	@Test
	void contieneSoloFechasDentroDeLosDosExtremosInclusivos() {
		PeriodoMensual periodo = new PeriodoMensual(2028, 2);
		assertTrue(periodo.contiene(LocalDate.of(2028, 2, 1)));
		assertTrue(periodo.contiene(LocalDate.of(2028, 2, 15)));
		assertTrue(periodo.contiene(LocalDate.of(2028, 2, 29)));
		assertFalse(periodo.contiene(LocalDate.of(2028, 1, 31)));
		assertFalse(periodo.contiene(periodo.finExclusivo()));
		assertFalse(periodo.contiene(LocalDate.of(2027, 2, 15)));
		assertFalse(periodo.contiene(null));
	}

	@Test
	void incluyeLasCincoSemanasDeUnMesQueEmpiezaEnJueves() {
		PeriodoMensual periodo = new PeriodoMensual(2026, 10);
		assertEquals(List.of(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5),
				LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)),
				periodo.iniciosSemanas());
	}

	@Test
	void conservaElLunesDelAnoAnteriorCuandoLaPrimeraSemanaLoCruza() {
		assertEquals(List.of(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 5),
				LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 19), LocalDate.of(2026, 1, 26)),
				new PeriodoMensual(2026, 1).iniciosSemanas());
	}

	@Test
	void incluyeUnaSemanaQueSoloComparteElDomingoConElMes() {
		assertEquals(List.of(LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 2),
				LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 23),
				LocalDate.of(2026, 3, 30)), new PeriodoMensual(2026, 3).iniciosSemanas());
	}

	@Test
	void febreroDeCuatroSemanasNoIncluyeLaSemanaSiguiente() {
		List<LocalDate> semanas = new PeriodoMensual(2021, 2).iniciosSemanas();
		assertEquals(List.of(LocalDate.of(2021, 2, 1), LocalDate.of(2021, 2, 8),
				LocalDate.of(2021, 2, 15), LocalDate.of(2021, 2, 22)), semanas);
		assertThrows(UnsupportedOperationException.class, () -> semanas.add(LocalDate.of(2021, 3, 1)));
	}

	@Test
	void resuelveLosLimitesEnLaZonaInstitucionalSolicitada() {
		PeriodoMensual periodo = new PeriodoMensual(2026, 10);
		ZoneId lima = ZoneId.of("America/Lima");
		assertEquals(Instant.parse("2026-10-01T05:00:00Z"), periodo.inicioInstantaneo(lima));
		assertEquals(Instant.parse("2026-11-01T05:00:00Z"), periodo.finExclusivoInstantaneo(lima));
		assertEquals(Instant.parse("2026-10-01T00:00:00Z"), periodo.inicioInstantaneo(ZoneOffset.UTC));
	}

	@ParameterizedTest
	@CsvSource({ "3, 743, 2026-03-01T05:00:00Z, 2026-04-01T04:00:00Z",
			"11, 721, 2026-11-01T04:00:00Z, 2026-12-01T05:00:00Z" })
	void calculaCadaLimiteLocalAunqueCambieLaDuracionRealDelMes(int mes, long horas,
			String inicioEsperado, String finEsperado) {
		PeriodoMensual periodo = new PeriodoMensual(2026, mes);
		ZoneId zona = ZoneId.of("America/New_York");
		Instant inicio = periodo.inicioInstantaneo(zona);
		Instant fin = periodo.finExclusivoInstantaneo(zona);
		assertEquals(Instant.parse(inicioEsperado), inicio);
		assertEquals(Instant.parse(finEsperado), fin);
		assertEquals(horas, Duration.between(inicio, fin).toHours());
		assertNotEquals(Duration.ofDays(periodo.cantidadDias()), Duration.between(inicio, fin));
	}

	@Test
	void rechazaZonaNulaConUnMensajeExplicitoEnAmbosLimites() {
		PeriodoMensual periodo = new PeriodoMensual(2026, 10);
		IllegalArgumentException errorInicio = assertThrows(IllegalArgumentException.class,
				() -> periodo.inicioInstantaneo(null));
		IllegalArgumentException errorFin = assertThrows(IllegalArgumentException.class,
				() -> periodo.finExclusivoInstantaneo(null));
		assertEquals("La zona horaria institucional es obligatoria.", errorInicio.getMessage());
		assertEquals(errorInicio.getMessage(), errorFin.getMessage());
	}
}
