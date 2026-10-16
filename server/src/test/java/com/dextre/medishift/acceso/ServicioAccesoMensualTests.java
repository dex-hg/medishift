package com.dextre.medishift.acceso;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioAccesoMensualTests {

	private final UUID institucion = UUID.randomUUID();
	private final UUID cuenta = UUID.randomUUID();
	private final RepositorioAccesoMensual accesos = mock(RepositorioAccesoMensual.class);
	private final RepositorioEspecialidades exclusiones = mock(RepositorioEspecialidades.class);
	private final ServicioAccesoMensual servicio = new ServicioAccesoMensual(accesos, exclusiones);

	@BeforeEach
	void prepararCuentaActiva() {
		when(accesos.cuentaActiva(institucion, cuenta)).thenReturn(true);
	}

	@Test
	void exigeAdministradorYReconsultaAnteRevocacion() {
		when(accesos.administradorActivo(institucion, cuenta)).thenReturn(true, false);
		assertDoesNotThrow(() -> servicio.exigirAdministrador(institucion, cuenta));
		CatalogoException fallo = assertThrows(CatalogoException.class,
				() -> servicio.exigirAdministrador(institucion, cuenta));
		assertEquals(HttpStatus.FORBIDDEN, fallo.obtenerEstado());
		verify(accesos, times(2)).administradorActivo(institucion, cuenta);
	}

	@Test
	void identificadoresAusentesNoPermitenConsultarNiInicializar() {
		clearInvocations(accesos);
		assertThrows(CatalogoException.class, () -> servicio.exigirAdministrador(null, cuenta));
		assertThrows(CatalogoException.class, () -> servicio.exigirAdministrador(institucion, null));
		assertThrows(CatalogoException.class, () -> servicio.inicializarAdministrador(null, cuenta));
		assertThrows(CatalogoException.class, () -> servicio.inicializarAdministrador(institucion, null));
		verifyNoInteractions(accesos, exclusiones);
	}

	@Test
	void inicializaDespuesDeBloquearYRegistraLaOperacion() {
		when(accesos.resolverRolAdministrador()).thenReturn(8);
		assertTrue(servicio.inicializarAdministrador(institucion, cuenta));
		var orden = inOrder(exclusiones, accesos);
		orden.verify(exclusiones).bloquearInstitucion(institucion);
		orden.verify(accesos).cuentaActiva(institucion, cuenta);
		orden.verify(accesos).administradorActivo(institucion, cuenta);
		orden.verify(accesos).existeAdministradorActivo(institucion);
		orden.verify(accesos).resolverRolAdministrador();
		orden.verify(accesos).asignarAdministrador(cuenta, 8);
		orden.verify(accesos).registrarInicializacion(institucion, cuenta);
	}

	@Test
	void repeticionNoAsignaOtroRolNiDuplicaElEvento() {
		when(accesos.administradorActivo(institucion, cuenta)).thenReturn(true);
		assertFalse(servicio.inicializarAdministrador(institucion, cuenta));
		verify(accesos, never()).existeAdministradorActivo(any());
		verify(accesos, never()).resolverRolAdministrador();
		verify(accesos, never()).asignarAdministrador(any(), anyInt());
		verify(accesos, never()).registrarInicializacion(any(), any());
	}

	@Test
	void noInicializaUnaCuentaAjenaInactivaODeInstitucionInactiva() {
		when(accesos.cuentaActiva(institucion, cuenta)).thenReturn(false);
		CatalogoException fallo = assertThrows(CatalogoException.class,
				() -> servicio.inicializarAdministrador(institucion, cuenta));
		assertEquals(HttpStatus.FORBIDDEN, fallo.obtenerEstado());
		verify(accesos, never()).administradorActivo(any(), any());
		verify(accesos, never()).resolverRolAdministrador();
		verify(accesos, never()).asignarAdministrador(any(), anyInt());
	}

	@Test
	void noUsaLaInicializacionParaAgregarOtroAdministrador() {
		when(accesos.existeAdministradorActivo(institucion)).thenReturn(true);
		CatalogoException fallo = assertThrows(CatalogoException.class,
				() -> servicio.inicializarAdministrador(institucion, cuenta));
		assertEquals(HttpStatus.CONFLICT, fallo.obtenerEstado());
		verify(accesos, never()).resolverRolAdministrador();
		verify(accesos, never()).asignarAdministrador(any(), anyInt());
		verify(accesos, never()).registrarInicializacion(any(), any());
	}

	@Test
	void propagaElFalloDeAuditoriaParaQueLaTransaccionNoConfirmeElAlta() {
		when(accesos.resolverRolAdministrador()).thenReturn(8);
		IllegalStateException fallo = new IllegalStateException("Fallo de auditoría");
		doThrow(fallo).when(accesos).registrarInicializacion(institucion, cuenta);
		assertSame(fallo, assertThrows(IllegalStateException.class,
				() -> servicio.inicializarAdministrador(institucion, cuenta)));
	}
}
