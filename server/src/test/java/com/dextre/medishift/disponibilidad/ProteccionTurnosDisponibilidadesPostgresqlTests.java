package com.dextre.medishift.disponibilidad;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-disponibilidades", matches = "true")
class ProteccionTurnosDisponibilidadesPostgresqlTests extends SoporteDisponibilidadesPostgresql {

	@ParameterizedTest
	@ValueSource(strings = { "draft", "pending", "approved" })
	void borrarDesactivarAcortarMoverYCambiarVigenciaRevierteSiTurnoFuturoPierdeCobertura(String estado) throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		UUID turno = crearTurno(estado);
		String cabecera = revision(id);
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion).header("If-Match", cabecera))
				.andExpect(status().isConflict());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("08:00", "16:00", "Inactivo"))
				.header("If-Match", cabecera)).andExpect(status().isConflict());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("08:00", "13:59", "Activo"))
				.header("If-Match", cabecera)).andExpect(status().isConflict());
		UUID nuevo = crearProfesional(cuenta.institucion(), "active");
		cliente.perform(json(put("/api/disponibilidades/{id}", id), disponibilidad(nuevo, 1, "08:00", "16:00",
				lunes, lunes.plusDays(6), "Activo")).header("If-Match", cabecera)).andExpect(status().isConflict());
		cliente.perform(json(put("/api/disponibilidades/{id}", id), disponibilidad(profesional, 1, "08:00", "16:00",
				lunes.plusDays(7), lunes.plusDays(13), "Activo")).header("If-Match", cabecera)).andExpect(status().isConflict());
		cliente.perform(get("/api/disponibilidades/{id}", id).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.horaFin").value("16:00")).andExpect(jsonPath("$.estado").value("Activo"))
				.andExpect(jsonPath("$.idProfesional").value(profesional.toString()))
				.andExpect(jsonPath("$.fechaInicio").value(lunes.toString()));
		assertEquals(cabecera, revision(id));
		assertEquals(estado, consultas.queryForObject("SELECT status_shift FROM shift WHERE id_shift = ?", String.class, turno));
	}

	@ParameterizedTest
	@ValueSource(strings = { "superseded", "cancelled" })
	void turnoCanceladoOSustituidoNoBloqueaEliminacionDeVentana(String estado) throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		crearTurno(estado);
		cliente.perform(delete("/api/disponibilidades/{id}", id).session(sesion).header("If-Match", revision(id)))
				.andExpect(status().isNoContent());
	}

	@Test
	void acortarHastaLimiteExactoDelTurnoConservaCoberturaYSePermite() throws Exception {
		UUID id = crearDisponibilidad(ventana("08:00", "16:00", "Activo"));
		crearTurno("approved");
		cliente.perform(json(put("/api/disponibilidades/{id}", id), ventana("08:00", "14:00", "Activo"))
				.header("If-Match", revision(id))).andExpect(status().isOk()).andExpect(jsonPath("$.horaFin").value("14:00"));
	}

	@Test
	void turnosPuedenCubrirseConUnionDeVentanasAdyacentesSinAceptarHuecos() throws Exception {
		UUID primera = crearDisponibilidad(ventana("08:00", "12:00", "Activo"));
		crearDisponibilidad(ventana("12:00", "16:00", "Activo"));
		crearTurno("pending");
		cliente.perform(json(put("/api/disponibilidades/{id}", primera), ventana("08:00", "12:00", "Activo"))
				.header("If-Match", revision(primera))).andExpect(status().isOk());
		cliente.perform(json(put("/api/disponibilidades/{id}", primera), ventana("08:00", "11:59", "Activo"))
				.header("If-Match", revision(primera))).andExpect(status().isConflict());
		cliente.perform(get("/api/disponibilidades/{id}", primera).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.horaFin").value("12:00"));
	}

}
