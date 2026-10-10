package com.dextre.medishift.programacion.mensual;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class IntervaloAsignacionTests {

	private final ZoneId lima = ZoneId.of("America/Lima");
	private final ZoneId nuevaYork = ZoneId.of("America/New_York");
	private final LocalDate fecha = LocalDate.of(2026, 10, 10);

	@Test
	void reparteUnTurnoNocturnoEntreLosDosDiasLocales() {
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(fecha, LocalTime.of(22, 0),
				fecha.plusDays(1), LocalTime.of(6, 0), lima);
		assertEquals(Instant.parse("2026-10-11T03:00:00Z"), intervalo.inicio());
		assertEquals(Instant.parse("2026-10-11T11:00:00Z"), intervalo.fin());
		assertEquals(Duration.ofHours(8), intervalo.duracion());
		assertEquals(Duration.ofHours(2), intervalo.duracionEnFecha(fecha, lima));
		assertEquals(Duration.ofHours(6), intervalo.duracionEnFecha(fecha.plusDays(1), lima));
		assertEquals(intervalo.duracion(), intervalo.duracionEnFecha(fecha, lima)
				.plus(intervalo.duracionEnFecha(fecha.plusDays(1), lima)));
	}

	@Test
	void unTurnoQueCruzaMesAportaSoloLaInterseccionACadaPeriodo() {
		LocalDate inicio = LocalDate.of(2026, 10, 31);
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(inicio, LocalTime.of(22, 0),
				inicio.plusDays(1), LocalTime.of(6, 0), lima);
		assertEquals(Duration.ofHours(2), intervalo.duracionEnPeriodo(new PeriodoMensual(2026, 10), lima));
		assertEquals(Duration.ofHours(6), intervalo.duracionEnPeriodo(new PeriodoMensual(2026, 11), lima));
		assertEquals(Duration.ZERO, intervalo.duracionEnPeriodo(new PeriodoMensual(2026, 12), lima));
	}

	@ParameterizedTest
	@CsvSource({ "2026-03-07, 7, 5", "2026-10-31, 9, 7" })
	void usaDuracionRealAlCruzarElCambioDeHora(String fechaInicio, long horasTotales, long horasFinales) {
		LocalDate inicio = LocalDate.parse(fechaInicio);
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(inicio, LocalTime.of(22, 0),
				inicio.plusDays(1), LocalTime.of(6, 0), nuevaYork);
		assertEquals(Duration.ofHours(horasTotales), intervalo.duracion());
		assertEquals(Duration.ofHours(2), intervalo.duracionEnFecha(inicio, nuevaYork));
		assertEquals(Duration.ofHours(horasFinales), intervalo.duracionEnFecha(inicio.plusDays(1), nuevaYork));
		assertNotEquals(Duration.ofHours(8), intervalo.duracion());
	}

	@Test
	void elRepartoDiarioDependeDeLaZonaInstitucionalRecibida() {
		IntervaloAsignacion intervalo = new IntervaloAsignacion(Instant.parse("2026-10-11T03:00:00Z"),
				Instant.parse("2026-10-11T11:00:00Z"));
		assertEquals(Duration.ofHours(2), intervalo.duracionEnFecha(fecha, lima));
		assertEquals(Duration.ZERO, intervalo.duracionEnFecha(fecha, ZoneId.of("UTC")));
		assertEquals(Duration.ofHours(8), intervalo.duracionEnFecha(fecha.plusDays(1), ZoneId.of("UTC")));
	}

	@Test
	void intervalosContiguosAlDiaOAlMesNoAportanDuracion() {
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(fecha, LocalTime.MIDNIGHT,
				fecha.plusDays(1), LocalTime.MIDNIGHT, lima);
		assertEquals(Duration.ofHours(24), intervalo.duracionEnFecha(fecha, lima));
		assertEquals(Duration.ZERO, intervalo.duracionEnFecha(fecha.minusDays(1), lima));
		assertEquals(Duration.ZERO, intervalo.duracionEnFecha(fecha.plusDays(1), lima));
		IntervaloAsignacion octubre = IntervaloAsignacion.desde(LocalDate.of(2026, 10, 1),
				LocalTime.MIDNIGHT, LocalDate.of(2026, 11, 1), LocalTime.MIDNIGHT, lima);
		assertEquals(Duration.ZERO, octubre.duracionEnPeriodo(new PeriodoMensual(2026, 9), lima));
		assertEquals(Duration.ZERO, octubre.duracionEnPeriodo(new PeriodoMensual(2026, 11), lima));
		assertEquals(octubre.duracion(), octubre.duracionEnPeriodo(new PeriodoMensual(2026, 10), lima));
	}

	@Test
	void unIntervaloAmplioSeRecortaAlDiaYAlMesCompletos() {
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(LocalDate.of(2026, 2, 15),
				LocalTime.MIDNIGHT, LocalDate.of(2026, 4, 15), LocalTime.MIDNIGHT, nuevaYork);
		assertEquals(Duration.ofHours(23), intervalo.duracionEnFecha(LocalDate.of(2026, 3, 8), nuevaYork));
		assertEquals(Duration.ofHours(743), intervalo.duracionEnPeriodo(new PeriodoMensual(2026, 3), nuevaYork));
	}

	@Test
	void conservaSegundosYNanosegundosAlCalcularLaInterseccion() {
		Instant medianoche = Instant.parse("2026-10-11T05:00:00Z");
		IntervaloAsignacion intervalo = new IntervaloAsignacion(medianoche.minusNanos(1),
				medianoche.plusNanos(2));
		assertEquals(Duration.ofNanos(3), intervalo.duracion());
		assertEquals(Duration.ofNanos(1), intervalo.duracionEnFecha(fecha, lima));
		assertEquals(Duration.ofNanos(2), intervalo.duracionEnFecha(fecha.plusDays(1), lima));
	}

	@Test
	void exigeIntervalosPositivosSinInferirLaFechaFinal() {
		Instant inicio = Instant.parse("2026-10-10T13:00:00Z");
		assertThrows(IllegalArgumentException.class, () -> new IntervaloAsignacion(inicio, inicio));
		assertThrows(IllegalArgumentException.class, () -> new IntervaloAsignacion(inicio, inicio.minusSeconds(1)));
		assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha, LocalTime.of(22, 0),
				fecha, LocalTime.of(6, 0), lima));
		assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha, LocalTime.of(8, 0),
				fecha, LocalTime.of(8, 0), lima));
		assertEquals(Duration.ofHours(24), IntervaloAsignacion.desde(fecha, LocalTime.of(8, 0),
				fecha.plusDays(1), LocalTime.of(8, 0), lima).duracion());
	}

	@ParameterizedTest
	@CsvSource({ "2026-03-08, 02:30, no existe", "2026-11-01, 01:30, se repite" })
	void rechazaHorasLocalesInexistentesOAmbiguasEnCualquierExtremo(String fechaTexto,
			String horaTexto, String detalle) {
		LocalDate dia = LocalDate.parse(fechaTexto);
		LocalTime hora = LocalTime.parse(horaTexto);
		IllegalArgumentException errorInicio = assertThrows(IllegalArgumentException.class,
				() -> IntervaloAsignacion.desde(dia, hora, dia, LocalTime.of(6, 0), nuevaYork));
		IllegalArgumentException errorFin = assertThrows(IllegalArgumentException.class,
				() -> IntervaloAsignacion.desde(dia, LocalTime.MIDNIGHT, dia, hora, nuevaYork));
		assertTrue(errorInicio.getMessage().contains(detalle));
		assertTrue(errorFin.getMessage().contains(detalle));
	}

	@Test
	void rechazaExtremosInstantaneosNulosConMensajesClaros() {
		Instant instante = Instant.parse("2026-10-10T13:00:00Z");
		assertEquals("El instante de inicio es obligatorio.", assertThrows(IllegalArgumentException.class,
				() -> new IntervaloAsignacion(null, instante)).getMessage());
		assertEquals("El instante de fin es obligatorio.", assertThrows(IllegalArgumentException.class,
				() -> new IntervaloAsignacion(instante, null)).getMessage());
	}

	@Test
	void exigeTodosLosComponentesLocalesYLaZona() {
		List<String> mensajes = List.of(
				assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(null,
						LocalTime.of(22, 0), fecha.plusDays(1), LocalTime.of(6, 0), lima)).getMessage(),
				assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha,
						null, fecha.plusDays(1), LocalTime.of(6, 0), lima)).getMessage(),
				assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha,
						LocalTime.of(22, 0), null, LocalTime.of(6, 0), lima)).getMessage(),
				assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha,
						LocalTime.of(22, 0), fecha.plusDays(1), null, lima)).getMessage(),
				assertThrows(IllegalArgumentException.class, () -> IntervaloAsignacion.desde(fecha,
						LocalTime.of(22, 0), fecha.plusDays(1), LocalTime.of(6, 0), null)).getMessage());
		assertEquals(List.of("La fecha de inicio es obligatoria.", "La hora de inicio es obligatoria.",
				"La fecha de fin explícita es obligatoria.", "La hora de fin es obligatoria.",
				"La zona horaria institucional es obligatoria."), mensajes);
	}

	@Test
	void exigeFechaPeriodoYZonaEnLosCalculosDeInterseccion() {
		IntervaloAsignacion intervalo = IntervaloAsignacion.desde(fecha, LocalTime.of(22, 0),
				fecha.plusDays(1), LocalTime.of(6, 0), lima);
		assertEquals("La fecha para calcular la duración es obligatoria.",
				assertThrows(IllegalArgumentException.class, () -> intervalo.duracionEnFecha(null, lima)).getMessage());
		assertEquals("El periodo mensual para calcular la duración es obligatorio.",
				assertThrows(IllegalArgumentException.class, () -> intervalo.duracionEnPeriodo(null, lima)).getMessage());
		assertEquals("La zona horaria institucional es obligatoria.",
				assertThrows(IllegalArgumentException.class, () -> intervalo.duracionEnFecha(fecha, null)).getMessage());
		assertEquals("La zona horaria institucional es obligatoria.",
				assertThrows(IllegalArgumentException.class,
						() -> intervalo.duracionEnPeriodo(new PeriodoMensual(2026, 10), null)).getMessage());
	}
}
