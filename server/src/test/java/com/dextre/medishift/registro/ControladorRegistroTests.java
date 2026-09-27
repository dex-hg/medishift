package com.dextre.medishift.registro;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControladorRegistroTests {

	private static final String JSON_VALIDO = """
			{"codigoInstitucion":" Clinica-Lima ","nombreInstitucion":" Clínica Lima ",
			 "zonaHoraria":"America/Lima","correo":" CUENTA@Institucion.PE ",
			 "contrasena":"clave-de-prueba"}
			""";
	private MockMvc cliente;
	private RepositorioRegistro repositorio;
	private PasswordEncoder codificador;
	private PlatformTransactionManager gestor;

	@BeforeEach
	void prepararCliente() {
		repositorio = mock(RepositorioRegistro.class);
		codificador = mock(PasswordEncoder.class);
		gestor = mock(PlatformTransactionManager.class);
		when(gestor.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
		when(codificador.encode(any())).thenReturn("hash-de-prueba");
		ServicioRegistro servicio = new ServicioRegistro(repositorio, codificador, gestor);
		cliente = MockMvcBuilders.standaloneSetup(new ControladorRegistro(servicio))
				.setControllerAdvice(new ManejadorErroresRegistro()).build();
	}

	@Test
	void devuelve201ConDatosNormalizadosYSinContrasena() throws Exception {
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idInstitucion").isString())
				.andExpect(jsonPath("$.idCuenta").isString())
				.andExpect(jsonPath("$.codigoInstitucion").value("Clinica-Lima"))
				.andExpect(jsonPath("$.nombreInstitucion").value("Clínica Lima"))
				.andExpect(jsonPath("$.correo").value("cuenta@institucion.pe"))
				.andExpect(jsonPath("$.contrasena").doesNotExist())
				.andExpect(content().string(not(containsString("hash-de-prueba"))));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "[]", "5", "{", "\"texto\"" })
	void rechazaJsonInvalidoSinAccesoARecursos(String json) throws Exception {
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").isString())
				.andExpect(jsonPath("$.errores").isMap());
		verifyNoInteractions(codificador, gestor, repositorio);
	}

	@Test
	void rechazaNumeroEnCampoTextoSinCoercion() throws Exception {
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON)
				.content(JSON_VALIDO.replace("\" Clinica-Lima \"", "123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.codigoInstitucion").exists());
		verifyNoInteractions(codificador, gestor, repositorio);
	}

	@Test
	void rechazaObjetoEnContrasena() throws Exception {
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON)
				.content(JSON_VALIDO.replace("\"clave-de-prueba\"", "{\"valor\":\"secreto\"}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.contrasena").exists())
				.andExpect(content().string(not(containsString("secreto"))));
		verifyNoInteractions(codificador, gestor, repositorio);
	}

	@Test
	void devuelveErroresPorCampoSinRepetirDatosSensibles() throws Exception {
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON)
				.content(JSON_VALIDO.replace("\"clave-de-prueba\"", "\"\u00a0\ufeff\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.contrasena").exists());
	}

	@Test
	void traduceCodigoDuplicadoA409() throws Exception {
		doThrow(new InstitucionDuplicadaException()).when(repositorio)
				.insertarInstitucion(any(UUID.class), any(DatosRegistro.class));
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errores.codigoInstitucion").exists());
	}

	@Test
	void traduceFalloDeConexionA503SinFiltrarDetalles() throws Exception {
		doThrow(new DataAccessResourceFailureException("SQL y credenciales de prueba"))
				.when(repositorio).insertarInstitucion(any(UUID.class), any(DatosRegistro.class));
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errores").isEmpty())
				.andExpect(content().string(not(containsString("credenciales"))));
	}

	@Test
	void traduceFalloAlAbrirTransaccionA503() throws Exception {
		when(gestor.getTransaction(any())).thenThrow(new CannotCreateTransactionException("Detalles internos"));
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void traduceFalloSqlNoRelacionadoConConexionA500Generico() throws Exception {
		doThrow(new DataIntegrityViolationException("INSERT y clave-de-prueba"))
				.when(repositorio).insertarCuenta(any(), any(), any(), any());
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(not(containsString("INSERT"))))
				.andExpect(content().string(not(containsString("clave-de-prueba"))));
	}

	@Test
	void traduceFalloInternoA500Generico() throws Exception {
		when(codificador.encode(any())).thenThrow(new IllegalStateException("Contraseña secreta"));
		cliente.perform(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(not(containsString("secreta"))));
	}

	@Test
	void conservaEstadosDeMetodoYTipoContenidoNoPermitidos() throws Exception {
		cliente.perform(get("/api/registro")).andExpect(status().isMethodNotAllowed());
		cliente.perform(post("/api/registro").contentType(MediaType.TEXT_PLAIN).content(JSON_VALIDO))
				.andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(codificador, gestor, repositorio);
	}

}
