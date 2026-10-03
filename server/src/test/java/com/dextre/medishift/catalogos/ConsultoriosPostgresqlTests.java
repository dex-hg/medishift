package com.dextre.medishift.catalogos;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-catalogos", matches = "true")
class ConsultoriosPostgresqlTests extends SoporteCatalogosPostgresql {

	@Test
	void persisteConsultorioGeneralEspecializadoInactivoYEliminacion() throws Exception {
		UUID id = crearRegistro("/api/consultorios", consultorio(" C-001 ", ""));
		cliente.perform(get("/api/consultorios/{id}", id).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value("C-001")).andExpect(jsonPath("$.especialidad").value(""));
		assertTrue(consultas.queryForObject("SELECT is_general_room FROM room WHERE id_room = ?", Boolean.class, id));
		String especialidad = nuevaEspecialidad();
		cliente.perform(json(put("/api/consultorios/{id}", id), consultorio("C-001", especialidad)))
				.andExpect(status().isOk());
		cliente.perform(get("/api/consultorios").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].especialidad").value(especialidad));
		assertFalse(consultas.queryForObject("SELECT is_general_room FROM room WHERE id_room = ?", Boolean.class, id));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ? AND id_specialty = ?",
				Integer.class, id, idEspecialidad(especialidad)));
		cliente.perform(json(put("/api/consultorios/{id}", id), consultorio("C-001", "").replace("Activo", "Inactivo")))
				.andExpect(status().isOk());
		cliente.perform(get("/api/consultorios/{id}", id).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("Inactivo")).andExpect(jsonPath("$.especialidad").value(""));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ?", Integer.class, id));
		assertTrue(consultas.queryForObject("SELECT is_general_room FROM room WHERE id_room = ?", Boolean.class, id));
		cliente.perform(delete("/api/consultorios/{id}", id).session(sesion)).andExpect(status().isNoContent());
		cliente.perform(get("/api/consultorios/{id}", id).session(sesion)).andExpect(status().isNotFound());
	}

	@Test
	void codigoDuplicadoRevierteCreacionYEdicionIncluidaEspecialidadNueva() throws Exception {
		crearRegistro("/api/consultorios", consultorio("C-001", ""));
		String nuevaCreacion = nuevaEspecialidad();
		cliente.perform(json(post("/api/consultorios"), consultorio("C-001", nuevaCreacion)))
				.andExpect(status().isConflict());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, nuevaCreacion));
		UUID segundo = crearRegistro("/api/consultorios", consultorio("C-002", ""));
		String nuevaEdicion = nuevaEspecialidad();
		cliente.perform(json(put("/api/consultorios/{id}", segundo), consultorio("C-001", nuevaEdicion)))
				.andExpect(status().isConflict());
		cliente.perform(get("/api/consultorios/{id}", segundo).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value("C-002")).andExpect(jsonPath("$.especialidad").value(""));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, nuevaEdicion));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ?", Integer.class, segundo));
	}

	@Test
	void institucionesAislanConsultoriosYPermitenMismoCodigo() throws Exception {
		UUID primero = crearRegistro("/api/consultorios", consultorio("C-001", ""));
		MockHttpSession sesionPrimera = sesion;
		sesion = sesionDe(crearCuenta());
		UUID segundo = crearRegistro("/api/consultorios", consultorio("C-001", ""));
		cliente.perform(get("/api/consultorios").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(segundo.toString()));
		cliente.perform(get("/api/consultorios/{id}", primero).session(sesion)).andExpect(status().isNotFound());
		cliente.perform(json(put("/api/consultorios/{id}", primero), consultorio("C-002", "")))
				.andExpect(status().isNotFound());
		cliente.perform(delete("/api/consultorios/{id}", primero).session(sesion)).andExpect(status().isNotFound());
		cliente.perform(get("/api/consultorios/{id}", primero).session(sesionPrimera)).andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value("C-001"));
	}

	@Test
	void bloqueoExistenteImpideEliminarYConservaRegistroYEspecialidad() throws Exception {
		String especialidad = crearEspecialidad();
		UUID consultorio = crearRegistro("/api/consultorios", consultorio("C-001", especialidad));
		UUID bloqueo = UUID.randomUUID();
		consultas.update("""
				INSERT INTO room_block VALUES (?, ?, '2027-01-04T08:00:00-05:00',
				'2027-01-04T12:00:00-05:00', 'Bloqueo temporal de prueba', 'active')
				""", bloqueo, consultorio);
		cliente.perform(delete("/api/consultorios/{id}", consultorio).session(sesion)).andExpect(status().isConflict());
		cliente.perform(get("/api/consultorios/{id}", consultorio).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.especialidad").value(especialidad));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ?", Integer.class, consultorio));
		consultas.update("DELETE FROM room_block WHERE id_room_block = ?", bloqueo);
		cliente.perform(delete("/api/consultorios/{id}", consultorio).session(sesion)).andExpect(status().isNoContent());
	}

	@Test
	void multiplesEspecialidadesSeConservanYElCambioAmbiguoSeRechaza() throws Exception {
		String primera = crearEspecialidad();
		String segunda = crearEspecialidad();
		UUID consultorio = crearRegistro("/api/consultorios", consultorio("C-001", primera));
		consultas.update("INSERT INTO room_specialty VALUES (?, ?)", consultorio, idEspecialidad(segunda));
		String seleccionada = JsonPath.read(cliente.perform(get("/api/consultorios/{id}", consultorio).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.especialidad");
		cliente.perform(json(put("/api/consultorios/{id}", consultorio), consultorio("C-002", seleccionada)))
				.andExpect(status().isOk());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ?", Integer.class, consultorio));
		String nueva = nuevaEspecialidad();
		cliente.perform(json(put("/api/consultorios/{id}", consultorio), consultorio("C-003", nueva)))
				.andExpect(status().isConflict());
		assertEquals("C-002", consultas.queryForObject("SELECT code_room FROM room WHERE id_room = ?", String.class, consultorio));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM room_specialty WHERE id_room = ?", Integer.class, consultorio));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?", Integer.class, nueva));
	}

	@Test
	void listaEspecialidadesRealesYRevocaSesionAlInactivarInstitucion() throws Exception {
		String especialidad = crearEspecialidad();
		cliente.perform(get("/api/especialidades").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(idEspecialidad(especialidad).toString())))
				.andExpect(jsonPath("$[*].nombre", hasItem(especialidad)));
		consultas.update("UPDATE institution SET status_institution = 'inactive' WHERE id_institution = ?", cuenta.institucion());
		cliente.perform(get("/api/consultorios").session(sesion)).andExpect(status().isUnauthorized());
		assertTrue(sesion.isInvalid());
	}
}
