package com.dextre.medishift.feriados;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

import static org.junit.jupiter.api.Assertions.*;

class ClienteFeriadosTests {

	private final ClienteFeriados cliente = new ClienteFeriados(JsonMapper.builder().build());

	@Test
	void soloImportaNacionalesPublicosYCombinaFechasRepetidas() {
		String respuesta = "[" + entrada("2026-11-01", "Celebración A", "PE", true, "null", "Public") + ","
				+ entrada("2026-11-01", "Celebración B", "PE", true, "null", "Public") + ","
				+ entrada("2026-11-02", "Regional", "PE", false, "[\"PE-LIM\"]", "Public") + ","
				+ entrada("2026-11-03", "Bancario", "PE", true, "null", "Bank") + ","
				+ entrada("2026-11-04", "Observancia", "PE", true, "null", "Observance") + "]";
		var dias = cliente.interpretar(respuesta.getBytes(StandardCharsets.UTF_8), Pais.PERU, 2026);
		assertEquals(1, dias.size());
		assertEquals("2026-11-01", dias.getFirst().fecha().toString());
		assertEquals("Celebración A / Celebración B", dias.getFirst().nombre());
	}

	@Test
	void noAcreditaDomingoDeResurreccionEnPeruYConservaLosFeriadosOficiales() {
		String respuesta = "[" + entrada("2026-04-03", "Good Friday", "PE", true, "null", "Public") + ","
				+ entrada("2026-04-05", "Easter Sunday", "PE", true, "null", "Public") + "]";
		assertEquals(1, cliente.interpretar(respuesta.getBytes(StandardCharsets.UTF_8), Pais.PERU, 2026).size());
		assertEquals(2, cliente.interpretar(respuesta.replace("\"PE\"", "\"CO\"").getBytes(StandardCharsets.UTF_8), Pais.COLOMBIA, 2026).size());
	}

	@Test
	void rechazaJsonIncompletoOtroPaisOtroAnioYFechasImposibles() {
		for (String respuesta : List.of("null", "[]", "{}", "[null]", "[{}]", "<html>Error</html>",
				"[" + entrada("2027-01-01", "Otro año", "PE", true, "null", "Public") + "]",
				"[" + entrada("2026-01-01", "Otro país", "CO", true, "null", "Public") + "]",
				"[" + entrada("2026-02-30", "Imposible", "PE", true, "null", "Public") + "]")) {
			assertThrows(CatalogoException.class, () -> cliente.interpretar(respuesta.getBytes(StandardCharsets.UTF_8), Pais.PERU, 2026));
		}
	}

	@Test
	void noConfundePaisesConElMismoDesfaseHorario() {
		assertEquals(Pais.PERU, Pais.deZona("America/Lima").orElseThrow());
		assertEquals(Pais.COLOMBIA, Pais.deZona("America/Bogota").orElseThrow());
		assertTrue(Pais.deZona("UTC").isEmpty());
		assertEquals(6, java.util.Arrays.stream(Pais.values()).map(Pais::codigo).distinct().count());
	}

	private String entrada(String fecha, String nombre, String pais, boolean nacional, String regiones, String tipo) {
		return "{\"date\":\"%s\",\"name\":\"%s\",\"countryCode\":\"%s\",\"nationalHoliday\":%s,\"subdivisionCodes\":%s,\"holidayTypes\":[\"%s\"]}"
				.formatted(fecha, nombre, pais, nacional, regiones, tipo);
	}
}
