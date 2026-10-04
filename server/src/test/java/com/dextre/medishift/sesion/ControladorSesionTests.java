package com.dextre.medishift.sesion;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dextre.medishift.sesion.RepositorioSesion.CuentaAutenticable;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControladorSesionTests {

	private static final String JSON_VALIDO = """
			{"codigoInstitucion":" Clinica-Lima ",
			 "correo":" CUENTA@Institucion.PE ","contrasena":" Clave exacta "}
			""";
	private MockMvc cliente;
	private RepositorioSesion repositorio;
	private PasswordEncoder codificador;
	private RespuestaSesion perfil;

	@BeforeEach
	void preparar() {
		repositorio = mock(RepositorioSesion.class);
		codificador = mock(PasswordEncoder.class);
		when(codificador.encode("verificacion-interna-medishift")).thenReturn("hash-ficticio");
		ServicioSesion servicio = new ServicioSesion(repositorio, codificador);
		perfil = new RespuestaSesion(UUID.randomUUID(), UUID.randomUUID(),
				"Clinica-Lima", "Clínica Lima", "cuenta@institucion.pe");
		cliente = MockMvcBuilders.standaloneSetup(new ControladorSesion(servicio))
				.setControllerAdvice(new ManejadorErroresSesion())
				.addFilters(new FiltroNoCacheSesion()).build();
	}

	@Test
	void post200RotaIdYGuardaSoloDosUuid() throws Exception {
		prepararCuentaValida();
		MockHttpSession anterior = new MockHttpSession();
		anterior.setAttribute("dato-anterior", "no-debe-seguir");
		String idAnterior = anterior.getId();
		MvcResult resultado = cliente.perform(post("/api/sesion").session(anterior)
				.contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.idCuenta").value(perfil.idCuenta().toString()))
				.andExpect(jsonPath("$.idInstitucion").value(perfil.idInstitucion().toString()))
				.andExpect(jsonPath("$.codigoInstitucion").value("Clinica-Lima"))
				.andExpect(jsonPath("$.nombreInstitucion").value("Clínica Lima"))
				.andExpect(jsonPath("$.correo").value("cuenta@institucion.pe"))
				.andExpect(jsonPath("$.contrasena").doesNotExist())
				.andReturn();
		MockHttpSession nueva = (MockHttpSession) resultado.getRequest().getSession(false);
		assertNotNull(nueva);
		assertTrue(anterior.isInvalid());
		assertNotEquals(idAnterior, nueva.getId());
		assertEquals(1800, nueva.getMaxInactiveInterval());
		assertEquals(perfil.idCuenta(), nueva.getAttribute(ControladorSesion.ATRIBUTO_ID_CUENTA));
		assertEquals(perfil.idInstitucion(), nueva.getAttribute(ControladorSesion.ATRIBUTO_ID_INSTITUCION));
		assertEquals(2, Collections.list(nueva.getAttributeNames()).size());
	}

	@Test
	void getSinSesionDevuelve401YNoCreaSesion() throws Exception {
		MvcResult resultado = cliente.perform(get("/api/sesion"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.errores").isEmpty()).andReturn();
		assertEquals(null, resultado.getRequest().getSession(false));
		verifyNoInteractions(repositorio);
	}

	@Test
	void getRevalidaEnBaseYRevocaSesionInactiva() throws Exception {
		MockHttpSession sesion = sesionValida();
		when(repositorio.buscarSesion(perfil.idInstitucion(), perfil.idCuenta()))
				.thenReturn(Optional.of(perfil)).thenReturn(Optional.empty());
		cliente.perform(get("/api/sesion").session(sesion))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.idCuenta").value(perfil.idCuenta().toString()));
		cliente.perform(get("/api/sesion").session(sesion))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"));
		assertTrue(sesion.isInvalid());
	}

	@Test
	void getRevocaSesionConAtributosMalformados() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		sesion.setAttribute(ControladorSesion.ATRIBUTO_ID_CUENTA, "uuid-en-texto");
		cliente.perform(get("/api/sesion").session(sesion)).andExpect(status().isUnauthorized());
		assertTrue(sesion.isInvalid());
		verifyNoInteractions(repositorio);
	}

	@Test
	void deleteEsIdempotenteYNoConsultaBase() throws Exception {
		MockHttpSession sesion = sesionValida();
		cliente.perform(delete("/api/sesion").session(sesion))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Cache-Control", "no-store"));
		assertTrue(sesion.isInvalid());
		cliente.perform(delete("/api/sesion"))
				.andExpect(status().isNoContent());
		verifyNoInteractions(repositorio);
	}

	@Test
	void postIncorrectoDevuelve401SinCrearSesionNiExponerDatos() throws Exception {
		when(repositorio.buscarCredenciales("Clinica-Lima", "cuenta@institucion.pe"))
				.thenReturn(Optional.empty());
		MvcResult resultado = cliente.perform(post("/api/sesion").contentType(MediaType.APPLICATION_JSON)
				.content(JSON_VALIDO))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(not(containsString("Clave exacta")))).andReturn();
		assertEquals(null, resultado.getRequest().getSession(false));
	}

	@Test
	void postDatosInvalidosYJsonIncorrectoDevuelven400() throws Exception {
		cliente.perform(post("/api/sesion").contentType(MediaType.APPLICATION_JSON)
				.content(JSON_VALIDO.replace("\" Clinica-Lima \"", "123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.codigoInstitucion").exists());
		cliente.perform(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores").isMap());
		verifyNoInteractions(repositorio);
	}

	@Test
	void baseNoDisponibleDevuelve503SinDatosInternos() throws Exception {
		when(repositorio.buscarSesion(perfil.idInstitucion(), perfil.idCuenta()))
				.thenThrow(new DataAccessResourceFailureException("SQL y datos secretos"));
		MockHttpSession sesion = sesionValida();
		cliente.perform(get("/api/sesion").session(sesion))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().string(not(containsString("secretos"))))
				.andExpect(header().string("Cache-Control", "no-store"));
		assertFalse(sesion.isInvalid());
	}

	private MockHttpSession sesionValida() {
		MockHttpSession sesion = new MockHttpSession();
		sesion.setAttribute(ControladorSesion.ATRIBUTO_ID_CUENTA, perfil.idCuenta());
		sesion.setAttribute(ControladorSesion.ATRIBUTO_ID_INSTITUCION, perfil.idInstitucion());
		return sesion;
	}

	private void prepararCuentaValida() {
		when(repositorio.buscarCredenciales("Clinica-Lima", "cuenta@institucion.pe"))
				.thenReturn(Optional.of(new CuentaAutenticable(perfil, "hash-real")));
		when(codificador.matches(" Clave exacta ", "hash-real")).thenReturn(true);
	}

}
