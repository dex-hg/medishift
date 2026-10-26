package com.dextre.medishift.feriados;

import java.time.Year;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.feriados.DatosFeriados.Pais;

import static org.junit.jupiter.api.Assertions.*;

/** Solo consulta país y año públicos. No accede a PostgreSQL ni crea archivos. */
@EnabledIfSystemProperty(named = "medishift.prueba-feriados", matches = "true")
class ProveedorFeriadosRealTests {

	@Test
	void losSeisPaisesRespondenAlAdaptadorRealConFechasCivilesYCorreccionPeruana() {
		var proveedor = new ClienteFeriados(JsonMapper.builder().build());
		int anio = Year.now(ZoneOffset.UTC).getValue();
		for (Pais pais : Pais.values()) {
			var dias = proveedor.consultar(pais, anio);
			assertFalse(dias.isEmpty(), pais.codigo());
			assertEquals(dias.size(), dias.stream().map(DatosFeriados.Feriado::fecha).distinct().count());
			assertTrue(dias.stream().allMatch(dia -> dia.fecha().getYear() == anio));
			if (pais == Pais.PERU) assertTrue(dias.stream().noneMatch(dia -> dia.nombre().equalsIgnoreCase("Easter Sunday")));
		}
	}
}
