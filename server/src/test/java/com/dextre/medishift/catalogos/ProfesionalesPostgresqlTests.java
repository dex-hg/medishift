package com.dextre.medishift.catalogos;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-catalogos", matches = "true")
class ProfesionalesPostgresqlTests extends SoporteCatalogosPostgresql {

	@Test
	void creaReleeEditaYEliminaProfesionalConEspecialidadPersistida() throws Exception {
		String especialidad = crearEspecialidad();
		UUID id = crearRegistro("/api/profesionales", profesional("CMP-001", " Ana María ", especialidad));
		cliente.perform(get("/api/profesionales/{id}", id).session(sesion))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Ana María"))
				.andExpect(jsonPath("$.correo").value("cmp-001@medishift.test"))
				.andExpect(jsonPath("$.especialidad").value(especialidad));
		assertEquals(cuenta.institucion(), consultas.queryForObject(
				"SELECT id_institution FROM professional WHERE id_professional = ?", UUID.class, id));
		assertEquals("active", consultas.queryForObject(
				"SELECT status_professional FROM professional WHERE id_professional = ?", String.class, id));
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM professional_specialty
				WHERE id_professional = ? AND id_specialty = ? AND is_primary_professional_specialty
				""", Integer.class, id, idEspecialidad(especialidad)));
		cliente.perform(json(put("/api/profesionales/{id}", id),
				profesional("CMP-001", "Nombre actualizado", especialidad).replace("Activo", "Inactivo")))
				.andExpect(status().isOk());
		cliente.perform(get("/api/profesionales").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id.toString()))
				.andExpect(jsonPath("$[0].nombres").value("Nombre actualizado"))
				.andExpect(jsonPath("$[0].estado").value("Inactivo"));
		assertEquals("inactive", consultas.queryForObject(
				"SELECT status_professional FROM professional WHERE id_professional = ?", String.class, id));
		cliente.perform(delete("/api/profesionales/{id}", id).session(sesion)).andExpect(status().isNoContent());
		cliente.perform(get("/api/profesionales/{id}", id).session(sesion)).andExpect(status().isNotFound());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM professional_specialty WHERE id_professional = ?",
				Integer.class, id));
	}

	@Test
	void duplicadoEnCreacionYEdicionRevierteEspecialidadNuevaYDatosParciales() throws Exception {
		String especialidad = crearEspecialidad();
		crearRegistro("/api/profesionales", profesional("CMP-001", "Primera", especialidad));
		String nuevaCreacion = nuevaEspecialidad();
		cliente.perform(json(post("/api/profesionales"), profesional("CMP-001", "Duplicada", nuevaCreacion)))
				.andExpect(status().isConflict());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, nuevaCreacion));
		UUID segundo = crearRegistro("/api/profesionales", profesional("CMP-002", "Segunda", especialidad));
		String nuevaEdicion = nuevaEspecialidad();
		cliente.perform(json(put("/api/profesionales/{id}", segundo),
				profesional("CMP-001", "Cambio rechazado", nuevaEdicion))).andExpect(status().isConflict());
		cliente.perform(get("/api/profesionales/{id}", segundo).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.colegiatura").value("CMP-002"))
				.andExpect(jsonPath("$.nombres").value("Segunda"))
				.andExpect(jsonPath("$.especialidad").value(especialidad));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?",
				Integer.class, nuevaEdicion));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM professional WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
	}

	@Test
	void correoDuplicadoNoCreaOtroProfesionalNiEspecialidadHuerfana() throws Exception {
		crearRegistro("/api/profesionales", profesional("CMP-001", "Primera", crearEspecialidad()));
		String nueva = nuevaEspecialidad();
		cliente.perform(json(post("/api/profesionales"),
				profesional("CMP-002", "Duplicada", nueva).replace("cmp-002@", "CMP-001@")))
				.andExpect(status().isConflict());
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM professional WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM specialty WHERE name_specialty = ?", Integer.class, nueva));
	}

	@Test
	void institucionesAislanLecturaEdicionEliminacionYPermitenMismaColegiatura() throws Exception {
		String especialidad = crearEspecialidad();
		UUID primero = crearRegistro("/api/profesionales", profesional("CMP-001", "Primera institución", especialidad));
		MockHttpSession sesionPrimera = sesion;
		CuentaPrueba otra = crearCuenta();
		sesion = sesionDe(otra);
		UUID segundo = crearRegistro("/api/profesionales", profesional("CMP-001", "Otra institución", especialidad));
		cliente.perform(get("/api/profesionales").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(segundo.toString()));
		cliente.perform(get("/api/profesionales/{id}", primero).session(sesion)).andExpect(status().isNotFound());
		cliente.perform(json(put("/api/profesionales/{id}", primero),
				profesional("CMP-002", "Acceso ajeno", especialidad))).andExpect(status().isNotFound());
		cliente.perform(delete("/api/profesionales/{id}", primero).session(sesion)).andExpect(status().isNotFound());
		cliente.perform(get("/api/profesionales/{id}", primero).session(sesionPrimera)).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombres").value("Primera institución"));
	}

	@Test
	void referenciaDeDisponibilidadImpideEliminarSinPerderEspecialidad() throws Exception {
		String especialidad = crearEspecialidad();
		UUID profesional = crearRegistro("/api/profesionales", profesional("CMP-001", "Profesional", especialidad));
		UUID disponibilidad = UUID.randomUUID();
		consultas.update("""
				INSERT INTO availability VALUES (?, ?, 1, '08:00', '12:00',
				'2027-01-01', '2027-01-31', 'active', NULL)
				""", disponibilidad, profesional);
		cliente.perform(delete("/api/profesionales/{id}", profesional).session(sesion))
				.andExpect(status().isConflict());
		cliente.perform(get("/api/profesionales/{id}", profesional).session(sesion))
				.andExpect(status().isOk()).andExpect(jsonPath("$.especialidad").value(especialidad));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM professional_specialty WHERE id_professional = ?",
				Integer.class, profesional));
		consultas.update("DELETE FROM availability WHERE id_availability = ?", disponibilidad);
		cliente.perform(delete("/api/profesionales/{id}", profesional).session(sesion))
				.andExpect(status().isNoContent());
	}

	@Test
	void cambiarPrimariaConTurnoHistoricoConservaCompetenciaAnteriorYBloqueaEliminacion() throws Exception {
		String anterior = crearEspecialidad();
		String nueva = nuevaEspecialidad();
		UUID profesional = crearRegistro("/api/profesionales", profesional("CMP-001", "Profesional", anterior));
		UUID consultorio = crearRegistro("/api/consultorios", consultorio("C-001", ""));
		UUID programacion = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule, id_institution, period_start_schedule, period_end_schedule,
				version_schedule, status_schedule, id_creator_user_account)
				VALUES (?, ?, '2027-01-01', '2027-01-31', 1, 'draft', ?)
				""", programacion, cuenta.institucion(), cuenta.cuenta());
		consultas.update("""
				INSERT INTO shift (id_shift, id_institution, id_schedule, id_professional, id_room,
				id_specialty, start_at_shift, end_at_shift, status_shift)
				VALUES (?, ?, ?, ?, ?, ?, '2027-01-04T08:00:00-05:00', '2027-01-04T12:00:00-05:00', 'draft')
				""", UUID.randomUUID(), cuenta.institucion(), programacion, profesional, consultorio, idEspecialidad(anterior));
		cliente.perform(json(put("/api/profesionales/{id}", profesional), profesional("CMP-001", "Editado", nueva)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.especialidad").value(nueva));
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM professional_specialty WHERE id_professional = ? AND id_specialty = ?
				AND NOT is_primary_professional_specialty
				""", Integer.class, profesional, idEspecialidad(anterior)));
		cliente.perform(delete("/api/profesionales/{id}", profesional).session(sesion)).andExpect(status().isConflict());
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM professional_specialty WHERE id_professional = ?",
				Integer.class, profesional));
		cliente.perform(get("/api/profesionales/{id}", profesional).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombres").value("Editado"));
	}

	@Test
	void bloquearCuentaRevocaSesionYEvitaEscritura() throws Exception {
		consultas.update("UPDATE user_account SET status_user_account = 'locked' WHERE id_user_account = ?", cuenta.cuenta());
		cliente.perform(json(post("/api/profesionales"), profesional("CMP-001", "No autorizada", nuevaEspecialidad())))
				.andExpect(status().isUnauthorized());
		assertTrue(sesion.isInvalid());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM professional WHERE id_institution = ?",
				Integer.class, cuenta.institucion()));
	}
}
