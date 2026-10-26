package com.dextre.medishift.programacion.mensual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/** El SQL portable se ejecuta únicamente en el esquema privado del ensayo. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class DatosDemostracionPostgresqlTests extends SoporteApiPlanesMensuales {

	@Autowired private PasswordEncoder codificador;

	@Test
	void cargaDosInstitucionesConCredencialesValidasYRecursosSinConcederRolALaCuentaAnterior() throws Exception {
		cargar();
		assertEquals(3, consultas.queryForObject("SELECT count(*) FROM institution", Integer.class));
		for (String pais : List.of("PE", "CO")) {
			UUID entidad = consultas.queryForObject("SELECT id_institution FROM institution WHERE code_institution=?", UUID.class, "DEMO-FERIADOS-" + pais);
			UUID cuenta = consultas.queryForObject("SELECT id_user_account FROM user_account WHERE id_institution=?", UUID.class, entidad);
			String hash = consultas.queryForObject("SELECT password_hash_user_account FROM user_account WHERE id_user_account=?", String.class, cuenta);
			assertTrue(codificador.matches("FeriadosDemo2026!", hash));
			assertDoesNotThrow(() -> aislar(() -> { accesos.exigirAdministrador(entidad, cuenta); return null; }));
			for (var tabla : java.util.Map.of("professional", 9, "specialty", 3, "planning_unit", 3, "room", 6,
					"schedule", 3, "shift", 12, "attendance", 2).entrySet()) {
				assertEquals(tabla.getValue(), consultas.queryForObject("SELECT count(*) FROM " + tabla.getKey()
						+ " WHERE id_institution=?", Integer.class, entidad));
			}
		}
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM user_role WHERE id_user_account=?", Integer.class, actor));
	}

	@Test
	void repetirLaCargaNoDuplicaNiCambiaNingunaFilaAnterior() throws Exception {
		cargar();
		String antes = huella();
		cargar();
		assertEquals(antes, huella());
	}

	@Test
	void codigoReservadoEnUsoRevierteTodaLaCargaSinAlterarDatos() throws Exception {
		UUID ajena = nuevaInstitucion();
		consultas.update("UPDATE institution SET code_institution='DEMO-FERIADOS-PE' WHERE id_institution=?", ajena);
		String antes = huella();
		assertThrows(DataAccessException.class, this::cargar);
		consultas.execute("ROLLBACK");
		assertEquals(antes, huella());
	}

	private void cargar() throws Exception {
		String sql = Files.readString(Path.of("../db/datos_demostracion.sql"));
		String rutaPublica = "SET LOCAL search_path = public, pg_catalog;";
		assertTrue(sql.contains(rutaPublica));
		assertEquals(sql.indexOf(rutaPublica), sql.lastIndexOf(rutaPublica));
		assertTrue(esquema.matches("prueba_mensual_[0-9a-f]{32}"));
		consultas.execute(sql.replace(rutaPublica, "SET LOCAL search_path = " + esquema + ", public, pg_catalog;"));
	}

	private String huella() {
		return String.join("\n", List.of("institution", "user_account", "user_role", "role", "specialty", "planning_unit",
				"room", "room_specialty", "professional", "professional_specialty", "schedule", "shift", "attendance", "audit_event")
				.stream().map(tabla -> consultas.queryForObject("SELECT md5(coalesce(string_agg(to_jsonb(fila)::text,'' "
						+ "ORDER BY to_jsonb(fila)::text),'')) FROM " + tabla + " fila", String.class)).toList());
	}
}
