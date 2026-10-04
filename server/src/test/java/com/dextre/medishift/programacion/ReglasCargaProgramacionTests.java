package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Requisito;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

import static org.junit.jupiter.api.Assertions.*;

class ReglasCargaProgramacionTests {

	private final ReglasCargaProgramacion reglas = new ReglasCargaProgramacion(6, 36);
	private final ZoneId zona = ZoneId.of("America/Lima");
	private final LocalDate lunes = LocalDate.of(2026, 10, 5);
	private final UUID profesional = UUID.randomUUID();
	private final UUID especialidad = UUID.randomUUID();

	@Test
	void sumaDuracionRealYCuentaAprobadosDeOtrosPeriodos() {
		Instant inicio = lunes.atTime(8, 0).atZone(zona).toInstant();
		Turno propuesto = turno(profesional, inicio, inicio.plusSeconds(5 * 3600));
		Turno aprobado = turno(profesional, inicio.plusSeconds(7 * 3600), inicio.plusSeconds(8 * 3600));
		assertDoesNotThrow(() -> reglas.validarHoras(List.of(propuesto), List.of(aprobado), lunes, zona));
		Turno excesivo = turno(profesional, inicio, inicio.plusSeconds(5 * 3600 + 1));
		assertThrows(CatalogoException.class, () -> reglas.validarHoras(List.of(excesivo), List.of(aprobado), lunes, zona));
	}

	@Test
	void sumaSemanaLocalRecortandoTurnosQueCruzanSuLimite() {
		Instant comienzo = lunes.atStartOfDay(zona).toInstant();
		Turno anterior = turno(profesional, comienzo.minusSeconds(3600), comienzo.plusSeconds(3600));
		Turno propuesto = turno(profesional, comienzo.plusSeconds(8 * 3600), comienzo.plusSeconds(13 * 3600));
		assertDoesNotThrow(() -> reglas.validarHoras(List.of(propuesto), List.of(anterior), lunes, zona));
		Turno exceso = turno(profesional, propuesto.inicio(), propuesto.fin().plusSeconds(1));
		assertThrows(CatalogoException.class, () -> reglas.validarHoras(List.of(exceso), List.of(anterior), lunes, zona));
	}

	@Test
	void treintaYSeisHorasSonInclusivasYNoSeMezclanProfesionales() {
		List<Turno> turnos = new ArrayList<>();
		for (int dia = 0; dia < 6; dia++) {
			Instant inicio = lunes.plusDays(dia).atTime(8, 0).atZone(zona).toInstant();
			turnos.add(turno(profesional, inicio, inicio.plusSeconds(6 * 3600)));
		}
		assertDoesNotThrow(() -> reglas.validarHoras(turnos, List.of(), lunes, zona));
		Instant domingo = lunes.plusDays(6).atTime(8, 0).atZone(zona).toInstant();
		Turno otro = turno(UUID.randomUUID(), domingo, domingo.plusSeconds(3600));
		assertDoesNotThrow(() -> reglas.validarHoras(turnos, List.of(otro), lunes, zona));
		turnos.add(turno(profesional, domingo, domingo.plusSeconds(1)));
		assertThrows(CatalogoException.class, () -> reglas.validarHoras(turnos, List.of(), lunes, zona));
	}

	@Test
	void coberturaNoSumaProfesionalesQueNuncaCoinciden() {
		Instant inicio = lunes.atTime(8, 0).atZone(zona).toInstant();
		Instant medio = inicio.plusSeconds(2 * 3600);
		Instant fin = inicio.plusSeconds(4 * 3600);
		List<Turno> turnos = List.of(turno(profesional, inicio, medio), turno(UUID.randomUUID(), medio, fin));
		assertDoesNotThrow(() -> reglas.validarCobertura(turnos, List.of(new Requisito(especialidad, inicio, fin, 1))));
		assertThrows(CatalogoException.class,
				() -> reglas.validarCobertura(turnos, List.of(new Requisito(especialidad, inicio, fin, 2))));
	}

	@Test
	void coberturaDetectaHuecosYCuentaPersonasDistintas() {
		Instant inicio = lunes.atTime(8, 0).atZone(zona).toInstant();
		Instant fin = inicio.plusSeconds(4 * 3600);
		List<Requisito> requisitos = List.of(new Requisito(especialidad, inicio, fin, 2));
		assertThrows(CatalogoException.class, () -> reglas.validarCobertura(List.of(
				turno(profesional, inicio, fin), turno(profesional, inicio, fin)), requisitos));
		assertDoesNotThrow(() -> reglas.validarCobertura(List.of(
				turno(profesional, inicio, fin), turno(UUID.randomUUID(), inicio, fin)), requisitos));
		assertThrows(CatalogoException.class, () -> reglas.validarCobertura(List.of(
				turno(profesional, inicio, fin.minusSeconds(1))), List.of(new Requisito(especialidad, inicio, fin, 1))));
	}

	@Test
	void noInventaRequisitosDeCoberturaYExigeLimitesPositivos() {
		assertDoesNotThrow(() -> reglas.validarCobertura(List.of(), List.of()));
		assertThrows(IllegalArgumentException.class, () -> new ReglasCargaProgramacion(0, 36));
		assertThrows(IllegalArgumentException.class, () -> new ReglasCargaProgramacion(6, -1));
	}

	@Test
	void noTruncaFraccionesDeSegundoEnTurnosImportados() {
		Instant inicio = lunes.atTime(8, 0).atZone(zona).toInstant();
		Turno legado = turno(profesional, inicio, inicio.plusSeconds(6 * 3600).plusNanos(1));
		assertThrows(CatalogoException.class, () -> reglas.validarHoras(List.of(legado), List.of(), lunes, zona));
	}

	private Turno turno(UUID persona, Instant inicio, Instant fin) {
		return new Turno(UUID.randomUUID(), UUID.randomUUID(), persona, UUID.randomUUID(), especialidad,
				inicio, fin, "draft", "", "Temporal", "Sala", "Especialidad");
	}
}
