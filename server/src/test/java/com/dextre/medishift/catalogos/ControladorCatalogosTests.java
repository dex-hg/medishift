package com.dextre.medishift.catalogos;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
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

class ControladorCatalogosTests {

	private static final String CONSULTORIO = """
			{"codigo":" c-001 ","nombre":"Consultorio","ubicacion":"Piso 1","estado":"Activo","especialidad":""}
			""";
	private MockMvc cliente;
	private ServicioCatalogos catalogos;
	private ServicioSesion sesiones;
	private MockHttpSession sesion;
	private UUID institucion;
	private UUID cuenta;

	@BeforeEach
	void prepararControladorConSesionRevalidada() {
		catalogos = mock(ServicioCatalogos.class);
		sesiones = mock(ServicioSesion.class);
		institucion = UUID.randomUUID();
		cuenta = UUID.randomUUID();
		sesion = new MockHttpSession();
		sesion.setAttribute("medishift.idCuenta", cuenta);
		sesion.setAttribute("medishift.idInstitucion", institucion);
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.of(
				new RespuestaSesion(cuenta, institucion, "Clinica", "Clínica", "cuenta@institucion.pe")));
		cliente = MockMvcBuilders.standaloneSetup(new ControladorCatalogos(
				new AccesoCatalogos(sesiones, "http://localhost:5173"), catalogos))
				.setControllerAdvice(new ManejadorErroresCatalogos()).addFilters(new FiltroNoCacheSesion()).build();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/profesionales", "/api/consultorios", "/api/especialidades" })
	void getSinSesionDevuelve401NoCacheYSinConsultaCatalogo(String ruta) throws Exception {
		cliente.perform(get(ruta)).andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"));
		verifyNoInteractions(catalogos, sesiones);
	}

	@Test
	void jsonNumericoYCampoInstitucionAjenaDevuelven400AntesDeEscribir() throws Exception {
		cliente.perform(post("/api/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(CONSULTORIO.replace("\" c-001 \"", "123")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.codigo").exists());
		cliente.perform(post("/api/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content(CONSULTORIO.replace("\"especialidad\":\"\"", "\"especialidad\":\"\",\"idInstitucion\":\"ajena\"")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.idInstitucion").exists());
		verifyNoInteractions(catalogos);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{", "[]", "123", "null" })
	void jsonMalformadoOConTipoRaizIncorrectoDevuelve400(String contenido) throws Exception {
		cliente.perform(post("/api/profesionales").session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido))
				.andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
		verifyNoInteractions(catalogos);
	}

	@Test
	void listaDeColeccionesVaciaDevuelveArrayYCacheDeshabilitada() throws Exception {
		when(catalogos.listarProfesionales(institucion)).thenReturn(List.of());
		cliente.perform(get("/api/profesionales").session(sesion)).andExpect(status().isOk())
				.andExpect(content().json("[]")).andExpect(header().string("Cache-Control", "no-store"));
		verify(catalogos).listarProfesionales(institucion);
	}

	@Test
	void post201Put200YDelete204UsanInstitucionDeSesionYDatosNormalizados() throws Exception {
		UUID id = UUID.randomUUID();
		Consultorio respuesta = new Consultorio(id, "C-001", "Consultorio", "Piso 1", "", "Activo");
		when(catalogos.crearConsultorio(eq(institucion), any())).thenReturn(respuesta);
		when(catalogos.actualizarConsultorio(eq(institucion), eq(id), any())).thenReturn(respuesta);
		cliente.perform(post("/api/consultorios").session(sesion).contentType(MediaType.APPLICATION_JSON).content(CONSULTORIO))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()));
		cliente.perform(put("/api/consultorios/{id}", id).session(sesion).contentType(MediaType.APPLICATION_JSON).content(CONSULTORIO))
				.andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("C-001"));
		cliente.perform(delete("/api/consultorios/{id}", id).session(sesion)).andExpect(status().isNoContent());
		Consultorio normalizado = new Consultorio(null, "C-001", "Consultorio", "Piso 1", "", "Activo");
		verify(catalogos).crearConsultorio(institucion, normalizado);
		verify(catalogos).actualizarConsultorio(institucion, id, normalizado);
		verify(catalogos).eliminarConsultorio(institucion, id);
	}

	@Test
	void originFrontendPermitidoYOrigenAjenoRechazadoSinMutacion() throws Exception {
		cliente.perform(post("/api/consultorios").session(sesion).header("Origin", "http://otro.test")
				.contentType(MediaType.APPLICATION_JSON).content(CONSULTORIO)).andExpect(status().isForbidden());
		verifyNoInteractions(catalogos);
		cliente.perform(post("/api/consultorios").session(sesion).header("Origin", "http://localhost:5173")
				.contentType(MediaType.APPLICATION_JSON).content(CONSULTORIO)).andExpect(status().isCreated());
		verify(catalogos).crearConsultorio(eq(institucion), any());
	}

	@Test
	void idAbreviadoDevuelve400AntesDeConsultarOEliminar() throws Exception {
		cliente.perform(get("/api/profesionales/1-1-1-1-1").session(sesion)).andExpect(status().isBadRequest());
		cliente.perform(delete("/api/consultorios/no-es-uuid").session(sesion)).andExpect(status().isBadRequest());
		verifyNoInteractions(catalogos);
	}

	@Test
	void perdidaDeConexionDevuelve503SinSqlNiCredenciales() throws Exception {
		when(sesiones.consultar(institucion, cuenta)).thenThrow(new DataAccessResourceFailureException("secretos-SQL"));
		cliente.perform(get("/api/profesionales").session(sesion)).andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(not(containsString("secretos-SQL"))));
		verifyNoInteractions(catalogos);
	}

	@Test
	void errorInesperadoDevuelve500SinDetallesInternosYConNoCache() throws Exception {
		when(catalogos.listarConsultorios(institucion)).thenThrow(new IllegalStateException("secretos-SQL"));
		cliente.perform(get("/api/consultorios").session(sesion)).andExpect(status().isInternalServerError())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(not(containsString("secretos-SQL"))));
	}

	@Test
	void restriccionFkSeTraduceA409SinDetallesInternos() throws Exception {
		UUID id = UUID.randomUUID();
		doThrow(new DataIntegrityViolationException("secretos-SQL", new SQLException("fk interna", "23503")))
				.when(catalogos).eliminarProfesional(institucion, id);
		cliente.perform(delete("/api/profesionales/{id}", id).session(sesion)).andExpect(status().isConflict())
				.andExpect(content().string(not(containsString("secretos-SQL"))));
	}
}
