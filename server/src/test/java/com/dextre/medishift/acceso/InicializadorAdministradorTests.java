package com.dextre.medishift.acceso;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InicializadorAdministradorTests {

	private final UUID institucion = UUID.randomUUID();
	private final UUID cuenta = UUID.randomUUID();
	private final ServicioAccesoMensual accesos = mock(ServicioAccesoMensual.class);
	private final ConfigurableApplicationContext contexto = mock(ConfigurableApplicationContext.class);
	private final Environment configuracion = mock(Environment.class);
	private final InicializadorAdministrador inicializador =
			new InicializadorAdministrador(accesos, contexto, configuracion);

	@Test
	void soloInicializaLosIdentificadoresExplicitosYCierraElProceso() {
		prepararIdentificadores();
		inicializador.run(null);
		verify(accesos).inicializarAdministrador(institucion, cuenta);
		verify(contexto).close();
	}

	@Test
	void rechazaLaEjecucionEnUnServidorWebAntesDeAccederACuentas() {
		ConfigurableApplicationContext contextoWeb = mock(ConfigurableApplicationContext.class,
				withSettings().extraInterfaces(WebApplicationContext.class));
		InicializadorAdministrador comando = new InicializadorAdministrador(accesos, contextoWeb, configuracion);
		IllegalStateException fallo = assertThrows(IllegalStateException.class, () -> comando.run(null));
		assertTrue(fallo.getMessage().contains("spring.main.web-application-type=none"));
		verifyNoInteractions(accesos, configuracion);
		verify(contextoWeb).close();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "incorrecto", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000 "})
	void exigeInstitucionCompletaSinSeleccionarCuentasAutomaticamente(String valor) {
		when(configuracion.getProperty("medishift.admin.institucion")).thenReturn(valor);
		IllegalStateException fallo = assertThrows(IllegalStateException.class, () -> inicializador.run(null));
		assertEquals("Debes indicar un UUID completo en medishift.admin.institucion.", fallo.getMessage());
		verifyNoInteractions(accesos);
		verify(contexto).close();
	}

	@Test
	void requiereTambienLaCuentaExplicita() {
		when(configuracion.getProperty("medishift.admin.institucion")).thenReturn(institucion.toString());
		IllegalStateException fallo = assertThrows(IllegalStateException.class, () -> inicializador.run(null));
		assertEquals("Debes indicar un UUID completo en medishift.admin.cuenta.", fallo.getMessage());
		verifyNoInteractions(accesos);
		verify(contexto).close();
	}

	@Test
	void cierraElProcesoTambienSiFallaLaTransaccion() {
		prepararIdentificadores();
		IllegalStateException fallo = new IllegalStateException("No se pudo completar la inicialización");
		doThrow(fallo).when(accesos).inicializarAdministrador(institucion, cuenta);
		assertSame(fallo, assertThrows(IllegalStateException.class, () -> inicializador.run(null)));
		verify(contexto).close();
	}

	private void prepararIdentificadores() {
		when(configuracion.getProperty("medishift.admin.institucion")).thenReturn(institucion.toString());
		when(configuracion.getProperty("medishift.admin.cuenta")).thenReturn(cuenta.toString());
	}
}
