package com.dextre.medishift.disponibilidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dextre.medishift.catalogos.AccesoCatalogos;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;
import com.dextre.medishift.sesion.FiltroNoCacheSesion;
import com.dextre.medishift.sesion.RespuestaSesion;
import com.dextre.medishift.sesion.ServicioSesion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControladorDisponibilidadesTests {

	private MockMvc cliente;
	private ServicioDisponibilidades disponibilidades;
	private ServicioSesion sesiones;
	private MockHttpSession sesion;
	private UUID institucion;
	private UUID cuenta;
	private UUID identificador;
	private UUID profesional;
	private Disponibilidad respuesta;
	private String contenido;
	private String revision;

	@BeforeEach
	void prepararControladorConSesionRevalidadaYRespuestaConRevision() {
		disponibilidades = mock(ServicioDisponibilidades.class);
		sesiones = mock(ServicioSesion.class);
		institucion = UUID.randomUUID();
		cuenta = UUID.randomUUID();
		identificador = UUID.randomUUID();
		profesional = UUID.randomUUID();
		revision = "a".repeat(64);
		respuesta = new Disponibilidad(identificador, profesional, "Ana Pérez", 1, "08:00", "16:00",
				"2027-01-04", "2027-01-31", "Activo", "", revision);
		contenido = """
				{"idProfesional":"%s","diaSemana":1,"horaInicio":"08:00","horaFin":"16:00",
				 "fechaInicio":"2027-01-04","fechaFin":"2027-01-31","estado":"Activo","observacion":""}
				""".formatted(profesional);
		sesion = new MockHttpSession();
		sesion.setAttribute("medishift.idCuenta", cuenta);
		sesion.setAttribute("medishift.idInstitucion", institucion);
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.of(
				new RespuestaSesion(cuenta, institucion, "Clinica", "Clínica", "cuenta@institucion.pe")));
		cliente = MockMvcBuilders.standaloneSetup(new ControladorDisponibilidades(
				new AccesoCatalogos(sesiones, "http://localhost:5173"), disponibilidades))
				.setControllerAdvice(new ManejadorErroresDisponibilidades()).addFilters(new FiltroNoCacheSesion()).build();
	}

	@Test
	void todasLasRutasExigenSesionYNoConsultanDatosSinElla() throws Exception {
		cliente.perform(get("/api/disponibilidades")).andExpect(status().isUnauthorized());
		cliente.perform(get("/api/disponibilidades/{id}", identificador)).andExpect(status().isUnauthorized());
		cliente.perform(post("/api/disponibilidades").contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isUnauthorized());
		cliente.perform(put("/api/disponibilidades/{id}", identificador)
				.contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isUnauthorized());
		cliente.perform(delete("/api/disponibilidades/{id}", identificador)).andExpect(status().isUnauthorized());
		verifyNoInteractions(disponibilidades, sesiones);
	}

	@Test
	void obtieneListaYRegistroIndividualConFormatosNombreRevisionETagYSinCache() throws Exception {
		when(disponibilidades.listar(institucion)).thenReturn(List.of(respuesta));
		when(disponibilidades.buscar(institucion, identificador)).thenReturn(respuesta);
		cliente.perform(get("/api/disponibilidades").session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profesional").value("Ana Pérez"))
				.andExpect(jsonPath("$[0].horaInicio").value("08:00"))
				.andExpect(jsonPath("$[0].diaSemana").value(1));
		cliente.perform(get("/api/disponibilidades/{id}", identificador).session(sesion))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("ETag", '"' + revision + '"'))
				.andExpect(jsonPath("$.revision").value(revision));
	}

	@Test
	void creacion201Edicion200YEliminacion204TransmitenInstitucionYRevision() throws Exception {
		when(disponibilidades.crear(eq(institucion), any())).thenReturn(respuesta);
		when(disponibilidades.actualizar(eq(institucion), eq(identificador), eq(revision), any())).thenReturn(respuesta);
		cliente.perform(post("/api/disponibilidades").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isCreated()).andExpect(header().string("ETag", '"' + revision + '"'));
		cliente.perform(put("/api/disponibilidades/{id}", identificador).session(sesion)
				.header("If-Match", '"' + revision + '"').contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isOk());
		cliente.perform(delete("/api/disponibilidades/{id}", identificador).session(sesion)
				.header("If-Match", '"' + revision + '"')).andExpect(status().isNoContent());
		verify(disponibilidades).actualizar(eq(institucion), eq(identificador), eq(revision), any());
		verify(disponibilidades).eliminar(institucion, identificador, revision);
	}

	@Test
	void editarOEliminarSinRevisionDevuelve428AntesDeTodaMutacion() throws Exception {
		cliente.perform(put("/api/disponibilidades/{id}", identificador).session(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isPreconditionRequired())
				.andExpect(jsonPath("$.errores.revision").exists());
		cliente.perform(delete("/api/disponibilidades/{id}", identificador).session(sesion))
				.andExpect(status().isPreconditionRequired());
		verifyNoInteractions(disponibilidades);
	}

	@Test
	void revisionObsoletaDevuelve409ConErrorDeRevision() throws Exception {
		doThrow(CatalogoException.conflicto("revision", "La disponibilidad cambió."))
				.when(disponibilidades).eliminar(institucion, identificador, revision);
		cliente.perform(delete("/api/disponibilidades/{id}", identificador).session(sesion)
				.header("If-Match", '"' + revision + '"')).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errores.revision").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{", "[]", "null", "123" })
	void raizJsonInvalidaDevuelve400SinLlegarAlServicio(String json) throws Exception {
		cliente.perform(post("/api/disponibilidades").session(sesion).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(disponibilidades);
	}

	@Test
	void tiposCoercionNumericaCamposAjenosYUuidAbreviadoSeRechazan() throws Exception {
		cliente.perform(post("/api/disponibilidades").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(contenido.replace("\"diaSemana\":1", "\"diaSemana\":\"1\"")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.diaSemana").exists());
		cliente.perform(post("/api/disponibilidades").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(contenido.replace("\"estado\":\"Activo\"", "\"idInstitucion\":\"ajena\",\"estado\":\"Activo\"")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.idInstitucion").exists());
		cliente.perform(get("/api/disponibilidades/1-1-1-1-1").session(sesion)).andExpect(status().isBadRequest());
		verifyNoInteractions(disponibilidades);
	}

	@Test
	void sesionRevocadaYOrigenAjenoImpidenMutaciones() throws Exception {
		cliente.perform(post("/api/disponibilidades").session(sesion).header("Origin", "http://otra.test")
				.contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isForbidden());
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.empty());
		cliente.perform(post("/api/disponibilidades").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(disponibilidades);
	}

	@Test
	void falloBaseDatosSeTraduceA503SinDetallesInternos() throws Exception {
		when(disponibilidades.listar(institucion)).thenThrow(new DataAccessResourceFailureException("SQL-clave-interna"));
		cliente.perform(get("/api/disponibilidades").session(sesion)).andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(not(containsString("SQL-clave-interna"))));
	}

}
