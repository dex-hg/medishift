package com.dextre.medishift.catalogos;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
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
		acceso = new AccesoCatalogos(sesiones);
		institucion = UUID.randomUUID();
		cuenta = UUID.randomUUID();
		perfil = new RespuestaSesion(cuenta, institucion, "Clinica", "Clínica", "cuenta@institucion.pe", false);
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
		when(sesiones.consultar(institucion, cuenta)).thenReturn(Optional.of(perfil)).thenReturn(Optional.empty());
		assertEquals(institucion, acceso.obtenerInstitucion(solicitud));
		assertThrows(AccesoNoAutorizadoException.class, () -> acceso.obtenerInstitucion(solicitud));
		assertTrue(sesion.isInvalid());
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
