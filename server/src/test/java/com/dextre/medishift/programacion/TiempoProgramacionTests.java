package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.TiempoProgramacion.Ventana;

import static org.junit.jupiter.api.Assertions.*;

class TiempoProgramacionTests {

	private final LocalDate lunes = LocalDate.of(2026, 10, 5);

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "2026-02-29", "2026-13-01", "2026-10-32", "2026-1-01", "0000-01-01",
			"2026-10-05T00:00:00Z", " 2026-10-05", "+10000-01-01" })
	void rechazaFechasImposiblesOFormatosAmbiguos(String valor) {
		assertThrows(CatalogoException.class, () -> TiempoProgramacion.analizarFecha(valor));
	}

	@Test
	void aceptaBisiestoYConservaFechaLocal() {
		assertEquals(LocalDate.of(2028, 2, 29), TiempoProgramacion.analizarFecha("2028-02-29"));
		assertEquals(lunes, TiempoProgramacion.analizarFecha("2026-10-05"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "24:00", "12:60", "7:00", "08:00:01", "08:00Z", " 08:00" })
	void rechazaHorasFueraDelContratoPorMinutos(String valor) {
		assertThrows(CatalogoException.class, () -> TiempoProgramacion.analizarHora(valor));
	}

	@Test
	void resuelveLimaSinUsarZonaDelServidor() {
		assertEquals(Instant.parse("2026-10-05T13:00:00Z"),
				TiempoProgramacion.resolver(lunes, LocalTime.of(8, 0), ZoneId.of("America/Lima")));
	}

	@Test
	void rechazaHoraInexistenteYHoraDuplicadaPorCambioEstacional() {
		ZoneId zona = ZoneId.of("America/New_York");
		assertThrows(CatalogoException.class, () -> TiempoProgramacion.resolver(
				LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), zona));
		assertThrows(CatalogoException.class, () -> TiempoProgramacion.resolver(
				LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), zona));
	}

	@Test
	void calculaLunesAlCruzarMesYAno() {
		assertEquals(LocalDate.of(2025, 12, 29), TiempoProgramacion.inicioSemana(LocalDate.of(2026, 1, 1)));
		assertEquals(lunes, TiempoProgramacion.inicioSemana(lunes.plusDays(6)));
	}

	@Test
	void uneVentanasContiguasAunqueLleguenDesordenadas() {
		assertTrue(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(10, 12), ventana(8, 10)),
				lunes, LocalTime.of(8, 0), LocalTime.of(12, 0)));
	}

	@Test
	void rechazaUnMinutoSinCoberturaYVentanasFueraDeVigencia() {
		Ventana tardia = new Ventana(1, LocalTime.of(10, 1), LocalTime.of(12, 0), lunes, lunes);
		assertFalse(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 10), tardia),
				lunes, LocalTime.of(8, 0), LocalTime.of(12, 0)));
		assertFalse(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 12)),
				lunes.plusWeeks(1), LocalTime.of(8, 0), LocalTime.of(12, 0)));
	}

	@Test
	void vigenciaInclusivaDiaCorrectoYLimitesExactos() {
		assertTrue(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 12)),
				lunes, LocalTime.of(8, 0), LocalTime.of(12, 0)));
		assertFalse(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 12)),
				lunes.plusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 0)));
		assertFalse(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 12)),
				lunes, LocalTime.of(7, 59), LocalTime.of(12, 0)));
		assertFalse(TiempoProgramacion.cubrirDisponibilidad(List.of(ventana(8, 12)),
				lunes, LocalTime.of(8, 0), LocalTime.of(12, 1)));
	}

	@Test
	void intervalosContiguosNoSeCruzanYRecortaDuracionALimites() {
		Instant inicio = Instant.parse("2026-10-05T13:00:00Z");
		Instant fin = inicio.plusSeconds(3600);
		assertFalse(TiempoProgramacion.seSuperponen(inicio, fin, fin, fin.plusSeconds(3600)));
		assertTrue(TiempoProgramacion.seSuperponen(inicio, fin, fin.minusSeconds(1), fin.plusSeconds(3600)));
		assertEquals(1800, TiempoProgramacion.segundosDentro(inicio, fin, inicio.plusSeconds(1800), fin));
		assertEquals(0, TiempoProgramacion.segundosDentro(inicio, fin, fin, fin.plusSeconds(3600)));
	}

	private Ventana ventana(int inicio, int fin) {
		return new Ventana(1, LocalTime.of(inicio, 0), LocalTime.of(fin, 0), lunes, lunes);
	}
}
