package com.dextre.medishift.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroSeguridadApiTests {

	private final FiltroSeguridadApi filtro = new FiltroSeguridadApi(
			"http://localhost:5173,http://127.0.0.1:5173,https://medishift.test", true, JsonMapper.shared());

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:8080", "http://localhost:5173", "http://127.0.0.1:5173", "https://medishift.test" })
	void aceptaOrigenesEsperadosYReproduceElJson(String origen) throws Exception {
		var solicitud = solicitud("POST");
		solicitud.addHeader(HttpHeaders.ORIGIN, origen);
		byte[] cuerpo = "{\"dato\":\"válido\"}".getBytes(StandardCharsets.UTF_8);
		solicitud.setContent(cuerpo);
		solicitud.setContentType(MediaType.APPLICATION_JSON_VALUE);
		var respuesta = new MockHttpServletResponse();
		var cadena = new MockFilterChain();

		filtro.doFilter(solicitud, respuesta, cadena);

		assertEquals(200, respuesta.getStatus());
		assertEquals("no-store", respuesta.getHeader(HttpHeaders.CACHE_CONTROL));
		assertEquals("nosniff", respuesta.getHeader("X-Content-Type-Options"));
		assertEquals("max-age=31536000", respuesta.getHeader("Strict-Transport-Security"));
		assertArrayEquals(cuerpo, cadena.getRequest().getInputStream().readAllBytes());
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://ajeno.test", "http://localhost:9000", "null", "http://",
			"http://usuario@localhost:8080", "http://localhost:8080/ruta", "http://localhost:8080?consulta=1",
			"http://localhost:8080#fragmento", "https://localhost:8080" })
	void rechazaOrigenesOpacosAjenosOMalformados(String origen) throws Exception {
		var solicitud = solicitud("POST");
		solicitud.addHeader(HttpHeaders.ORIGIN, origen);
		comprobarRechazo(solicitud, 403);
	}

	@Test
	void contrastaRefererYFetchMetadataParaEscriturasSinOrigin() throws Exception {
		var solicitud = solicitud("DELETE");
		solicitud.addHeader("Sec-Fetch-Site", "cross-site");
		comprobarRechazo(solicitud, 403);
		var referenciaAjena = solicitud("POST");
		referenciaAjena.addHeader(HttpHeaders.REFERER, "https://ajeno.test/formulario");
		comprobarRechazo(referenciaAjena, 403);
		var sinProcedencia = solicitud("DELETE");
		sinProcedencia.addHeader(HttpHeaders.COOKIE, "JSESSIONID=sesion-de-prueba");
		comprobarRechazo(sinProcedencia, 403);
		var referenciaPermitida = solicitud("POST");
		referenciaPermitida.addHeader(HttpHeaders.REFERER, "https://medishift.test/horarios?mes=2032-01");
		var cadena = new MockFilterChain();
		filtro.doFilter(referenciaPermitida, new MockHttpServletResponse(), cadena);
		assertNotNull(cadena.getRequest());
	}

	@Test
	void rechazaCuerpoExcesivoConYSinLongitudDeclarada() throws Exception {
		byte[] exceso = new byte[FiltroSeguridadApi.MAXIMO_CUERPO + 1];
		var conLongitud = solicitud("POST");
		conLongitud.setContent(exceso);
		comprobarRechazo(conLongitud, 413);

		var sinLongitud = new MockHttpServletRequest("POST", "/api/registro") {
			@Override
			public long getContentLengthLong() { return -1; }
		};
		sinLongitud.setContent(exceso);
		sinLongitud.setContentType(MediaType.APPLICATION_JSON_VALUE);
		comprobarRechazo(sinLongitud, 413);
	}

	@Test
	void rechazaCuerposNoJsonYParametrosRepetidos() throws Exception {
		var formulario = solicitud("POST");
		formulario.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
		formulario.setContent("correo=ejemplo%40medishift.test".getBytes(StandardCharsets.UTF_8));
		comprobarRechazo(formulario, 415);

		var consulta = solicitud("GET");
		consulta.setQueryString("pagina=0&pagina=1");
		consulta.setParameter("pagina", "0", "1");
		comprobarRechazo(consulta, 400);
	}

	@Test
	void venceLaSesionAunqueContinueRecibiendoSolicitudes() throws Exception {
		var antigua = new MockHttpSession() {
			@Override
			public long getCreationTime() {
				return System.currentTimeMillis() - Duration.ofHours(9).toMillis();
			}
		};
		var solicitud = solicitud("GET");
		solicitud.setSession(antigua);
		var cadena = new MockFilterChain();
		filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);

		assertTrue(antigua.isInvalid());
		assertNull(((HttpServletRequest) cadena.getRequest()).getSession(false));
	}

	private void comprobarRechazo(MockHttpServletRequest solicitud, int esperado) throws Exception {
		var respuesta = new MockHttpServletResponse();
		var cadena = new MockFilterChain();
		filtro.doFilter(solicitud, respuesta, cadena);

		assertEquals(esperado, respuesta.getStatus());
		assertNotNull(JsonMapper.shared().readTree(respuesta.getContentAsByteArray()).get("mensaje"));
		assertNull(cadena.getRequest());
	}

	private MockHttpServletRequest solicitud(String metodo) {
		var solicitud = new MockHttpServletRequest(metodo, "/api/sesion");
		solicitud.setServerName("localhost");
		solicitud.setServerPort(8080);
		return solicitud;
	}
}
