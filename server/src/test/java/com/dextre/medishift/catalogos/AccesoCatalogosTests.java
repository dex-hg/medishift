package com.dextre.medishift.catalogos;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.RespuestaSesion;
import com.dextre.medishift.sesion.ServicioSesion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccesoCatalogosTests {

	private ServicioSesion sesiones;
	private AccesoCatalogos acceso;
	private UUID institucion;
	private UUID cuenta;
	private RespuestaSesion perfil;

	@BeforeEach
	void preparar() {
		sesiones = mock(ServicioSesion.class);
		acceso = new AccesoCatalogos(sesiones, "http://localhost:5173,http://127.0.0.1:5173");
		institucion = UUID.randomUUID();
		cuenta = UUID.randomUUID();
		perfil = new RespuestaSesion(cuenta, institucion, "Clinica", "Clínica", "cuenta@institucion.pe");
	}

	@Test
	void solicitudSinSesionNoCreaSesionNiConsultaBase() {
		MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", "/api/profesionales");
		assertThrows(AccesoNoAutorizadoException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertNull(solicitud.getSession(false));
		verifyNoInteractions(sesiones);
	}

	@Test
	void idsEnTextoOSinInstitucionInvalidanSesionSinConsulta() {
		MockHttpSession sesion = new MockHttpSession();
		sesion.setAttribute("medishift.idCuenta", cuenta.toString());
		sesion.setAttribute("medishift.idInstitucion", institucion);
		MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", "/api/profesionales");
		solicitud.setSession(sesion);
		assertThrows(AccesoNoAutorizadoException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertTrue(sesion.isInvalid());
		verifyNoInteractions(sesiones);
	}

	@Test
	void cadaSolicitudRevalidaCuentaYRevocacionInvalidaSesion() {
		MockHttpServletRequest solicitud = solicitudActiva("GET");
		MockHttpSession sesion = (MockHttpSession) solicitud.getSession(false);
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.of(perfil), Optional.empty());
		assertEquals(institucion, acceso.obtenerInstitucion(solicitud));
		assertThrows(AccesoNoAutorizadoException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertTrue(sesion.isInvalid());
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:8080", "http://localhost:5173", "http://127.0.0.1:5173" })
	void permiteOrigenDelServidorYFrontendConfigurado(String origen) {
		MockHttpServletRequest solicitud = solicitudActiva("POST");
		solicitud.addHeader("Origin", origen);
		assertEquals(institucion, acceso.obtenerInstitucion(solicitud));
		verify(sesiones).consultar(institucion, cuenta);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://ajeno.test", "http://localhost:9000", "null", "http://",
			"http://usuario@localhost:8080", "http://localhost:8080/ruta", "http://localhost:8080?consulta=1",
			"http://localhost:8080#fragmento", "javascript:alert(1)", "https://localhost:8080" })
	void rechazaOrigenMaliciosoOpacoMalformadoYNoConfigurado(String origen) {
		MockHttpServletRequest solicitud = solicitudActiva("PUT");
		solicitud.addHeader("Origin", origen);
		CatalogoException error = assertThrows(CatalogoException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertEquals(HttpStatus.FORBIDDEN, error.obtenerEstado());
		assertFalse(((MockHttpSession) solicitud.getSession(false)).isInvalid());
	}

	@Test
	void fetchCrossSiteSinOriginTambienSeRechazaPeroGetNoModifica() {
		MockHttpServletRequest solicitud = solicitudActiva("DELETE");
		solicitud.addHeader("Sec-Fetch-Site", "cross-site");
		assertEquals(HttpStatus.FORBIDDEN,
				assertThrows(CatalogoException.class, () -> acceso.obtenerInstitucion(solicitud)).obtenerEstado());
		solicitud.setMethod("GET");
		assertEquals(institucion, acceso.obtenerInstitucion(solicitud));
	}

	@Test
	void falloDeConexionSePropagaSinRevocarCuentaActiva() {
		MockHttpServletRequest solicitud = solicitudActiva("GET");
		when(sesiones.consultar(institucion, cuenta)).thenThrow(new DataAccessResourceFailureException("inaccesible"));
		assertThrows(DataAccessResourceFailureException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertFalse(((MockHttpSession) solicitud.getSession(false)).isInvalid());
	}

	private MockHttpServletRequest solicitudActiva(String metodo) {
		MockHttpSession sesion = new MockHttpSession();
		sesion.setAttribute("medishift.idCuenta", cuenta);
		sesion.setAttribute("medishift.idInstitucion", institucion);
		MockHttpServletRequest solicitud = new MockHttpServletRequest(metodo, "/api/profesionales");
		solicitud.setSession(sesion);
		solicitud.setServerName("localhost");
		solicitud.setServerPort(8080);
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.of(perfil));
		return solicitud;
	}
}
