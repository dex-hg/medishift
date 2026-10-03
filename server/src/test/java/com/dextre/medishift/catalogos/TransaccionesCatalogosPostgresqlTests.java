package com.dextre.medishift.catalogos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dextre.medishift.MedishiftApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { MedishiftApplication.class, TransaccionesCatalogosPostgresqlTests.ConfiguracionFallos.class })
@EnabledIfSystemProperty(named = "medishift.prueba-catalogos", matches = "true")
class TransaccionesCatalogosPostgresqlTests extends SoporteCatalogosPostgresql {

	@Autowired
	@Qualifier("profesionalesConFallo")
	private RepositorioProfesionales profesionales;
	@Autowired
	@Qualifier("consultoriosConFallo")
	private RepositorioConsultorios consultorios;

	@BeforeEach
	void restaurarRepositoriosReales() {
		reset(profesionales, consultorios);
	}

	@Test
	void falloDeVinculoRevierteInsertProfesionalYEspecialidadYaPersistidos() throws Exception {
		String especialidad = nuevaEspecialidad();
		doThrow(new DataIntegrityViolationException("Fallo inducido al vincular la especialidad"))
				.when(profesionales).asignarEspecialidad(any(), any(), any());
		cliente.perform(json(post("/api/profesionales"), profesional("CMP-001", "Temporal", especialidad)))
				.andExpect(status().isInternalServerError());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM professional WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, especialidad));
	}

	@Test
	void falloDeVinculoRevierteInsertConsultorioYEspecialidadYaPersistidos() throws Exception {
		String especialidad = nuevaEspecialidad();
		doThrow(new DataIntegrityViolationException("Fallo inducido al vincular la especialidad"))
				.when(consultorios).asignarEspecialidad(any(), any(), any());
		cliente.perform(json(post("/api/consultorios"), consultorio("C-001", especialidad)))
				.andExpect(status().isInternalServerError());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM room WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, especialidad));
	}

	@TestConfiguration
	static class ConfiguracionFallos {
		@Bean
		@Primary
		RepositorioProfesionales profesionalesConFallo(JdbcTemplate consultas) {
			return spy(new RepositorioProfesionales(consultas));
		}

		@Bean
		@Primary
		RepositorioConsultorios consultoriosConFallo(JdbcTemplate consultas) {
			return spy(new RepositorioConsultorios(consultas));
		}
	}
}
