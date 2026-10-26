package com.dextre.medishift.feriados;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.feriados.DatosFeriados.CalendarioAnual;
import com.dextre.medishift.feriados.DatosFeriados.Feriado;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

import static org.junit.jupiter.api.Assertions.*;

class RepositorioFeriadosTests {

	@TempDir Path directorio;

	@Test
	void laCopiaPersisteAlRecrearElRepositorioYNoSeMezclanPaises() throws Exception {
		var repositorio = repositorio();
		var calendario = calendario();
		repositorio.guardar(calendario, Pais.PERU);
		assertEquals(calendario, repositorio().leer(Pais.PERU, 2026).orElseThrow());
		assertTrue(repositorio.leer(Pais.COLOMBIA, 2026).isEmpty());
		assertTrue(repositorio.leer(Pais.PERU, 2027).isEmpty());
		try (var archivos = Files.list(directorio)) { assertEquals(List.of("PE-2026.json"), archivos.map(ruta -> ruta.getFileName().toString()).toList()); }
	}

	@Test
	void noSustituyeUnaCopiaCorrectaConDatosInvalidos() {
		var repositorio = repositorio();
		repositorio.guardar(calendario(), Pais.PERU);
		var invalido = new CalendarioAnual("PE", 2026, Instant.now(), List.of(
				new Feriado(LocalDate.of(2026, 11, 1), "Uno"), new Feriado(LocalDate.of(2026, 11, 1), "Duplicado")));
		assertThrows(IllegalArgumentException.class, () -> repositorio.guardar(invalido, Pais.PERU));
		assertEquals(calendario().dias(), repositorio.leer(Pais.PERU, 2026).orElseThrow().dias());
	}

	@Test
	void archivoCorruptoNoSeInterpretaComoUnAnioSinFeriados() throws Exception {
		Files.writeString(directorio.resolve("PE-2026.json"), "{corrupto");
		assertTrue(repositorio().leer(Pais.PERU, 2026).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> repositorio().leer(Pais.PERU, 0));
	}

	private RepositorioFeriados repositorio() { return new RepositorioFeriados(JsonMapper.builder().build(), directorio.toString()); }
	private CalendarioAnual calendario() { return new CalendarioAnual("PE", 2026, Instant.parse("2026-10-26T15:00:00Z"),
			List.of(new Feriado(LocalDate.of(2026, 11, 1), "Feriado de ensayo"))); }
}
