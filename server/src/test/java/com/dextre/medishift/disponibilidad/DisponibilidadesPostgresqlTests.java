package com.dextre.medishift.disponibilidad;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-disponibilidades", matches = "true")
class DisponibilidadesPostgresqlTests extends SoporteDisponibilidadesPostgresql {

	@Test
	void creaReleeEditaYEliminaVentanaConPersistenciaNombreYRevision() throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.profesional").value("Ana María Pérez Díaz"))
				.andExpect(jsonPath("$.horaInicio").value("08:00")).andExpect(jsonPath("$.horaFin").value("16:00"));
		String anterior = revision(id);
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("09:00", "15:00", "Inactivo"))
				.header("If-Match", anterior)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Inactivo"));
		assertEquals("inactive", consultas.queryForObject("SELECT status_availability FROM availability WHERE id_availability = ?",
				String.class, id));
		cliente.perform(get("/api/disponibilidades").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].horaInicio").value("09:00"));
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion).header("If-Match", revision(id)))
				.andExpect(status().isNoContent());
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isNotFound());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM availability WHERE id_availability = ?", Integer.class, id));
	}

	@Test
	void duplicadosYCrucesSeRechazanPeroAdyacenciasYCrucesInactivosSonValidos() throws Exception {
		crearDisponibilidad(ventana("08:00", "12:00", "Activo"));
		cliente.perform(json(post("/api/disponibilidades"), ventana("08:00", "12:00", "Activo")))
				.andExpect(status().isConflict());
		cliente.perform(json(post("/api/disponibilidades"), ventana("11:59", "16:00", "Activo")))
				.andExpect(status().isConflict());
		crearDisponibilidad(ventana("12:00", "16:00", "Activo"));
		UUID inactiva = crearDisponibilidad(ventana("08:00", "16:00", "Inactivo"));
		cliente.perform(json(put("/api/disponibilidades/{id}", inactiva), ventana("08:00", "16:00", "Activo"))
				.header("If-Match", revision(inactiva))).andExpect(status().isConflict());
		assertEquals("inactive", consultas.queryForObject("SELECT status_availability FROM availability WHERE id_availability = ?",
				String.class, inactiva));
	}

	@Test
	void interseccionDeFechasSoloConflictaCuandoContieneElDiaSemanalElegido() throws Exception {
		LocalDate primero = LocalDate.of(2027, 1, 4);
		crearDisponibilidad(disponibilidad(profesional, 1, "08:00", "16:00", primero, primero.plusDays(6), "Activo"));
		crearDisponibilidad(disponibilidad(profesional, 1, "08:00", "16:00", primero.plusDays(1), primero.plusDays(13), "Activo"));
		cliente.perform(json(post("/api/disponibilidades"), disponibilidad(profesional, 1, "08:00", "16:00",
				primero, primero, "Activo"))).andExpect(status().isConflict());
		crearDisponibilidad(disponibilidad(profesional, 2, "08:00", "16:00", primero, primero.plusDays(6), "Activo"));
	}

	@Test
	void revisionObsoletaOFaltanteImpideSobrescribirUnaEdicionMasReciente() throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		String anterior = revision(id);
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("09:00", "15:00", "Activo")))
				.andExpect(status().isPreconditionRequired());
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isPreconditionRequired());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("09:00", "15:00", "Activo"))
				.header("If-Match", anterior)).andExpect(status().isOk());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("08:00", "16:00", "Activo"))
				.header("If-Match", anterior)).andExpect(status().isConflict());
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion).header("If-Match", anterior))
				.andExpect(status().isConflict());
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.horaInicio").value("09:00"));
	}

	@Test
	void institucionesAislanLecturaEdicionEliminacionYSeleccionDeProfesional() throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		String cabecera = revision(id);
		MockHttpSession sesionAnterior = sesion;
		CuentaPrueba otra = crearCuenta();
		UUID profesionalAjeno = crearProfesional(otra.institucion(), "active");
		cliente.perform(json(post("/api/disponibilidades"), disponibilidad(profesionalAjeno, 1, "08:00", "16:00",
				lunes, lunes.plusDays(6), "Activo"))).andExpect(status().isNotFound());
		sesion = sesionDe(otra);
		cliente.perform(get("/api/disponibilidades").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isNotFound());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), disponibilidad(profesionalAjeno, 1, "08:00", "16:00",
				lunes, lunes.plusDays(6), "Activo")).header("If-Match", cabecera)).andExpect(status().isNotFound());
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion).header("If-Match", cabecera))
				.andExpect(status().isNotFound());
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesionAnterior)).andExpect(status().isOk());
	}

	@Test
	void profesionalInactivoYCalendarioInvalidoNoCreanDisponibilidades() throws Exception {
		UUID inactivo = crearProfesional(cuenta.institucion(), "inactive");
		cliente.perform(json(post("/api/disponibilidades"), disponibilidad(inactivo, 1, "08:00", "16:00",
				lunes, lunes.plusDays(6), "Activo"))).andExpect(status().isConflict());
		cliente.perform(json(post("/api/disponibilidades"), ventana("16:00", "08:00", "Activo")))
				.andExpect(status().isBadRequest());
		cliente.perform(json(post("/api/disponibilidades"), disponibilidad(profesional, 2, "08:00", "16:00",
				lunes, lunes, "Activo"))).andExpect(status().isBadRequest());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM availability WHERE id_professional = ?",
				Integer.class, profesional));
	}

}
