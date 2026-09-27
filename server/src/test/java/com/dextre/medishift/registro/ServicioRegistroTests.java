package com.dextre.medishift.registro;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioRegistroTests {

	private RepositorioRegistro repositorio;
	private PasswordEncoder codificador;
	private PlatformTransactionManager gestor;
	private SimpleTransactionStatus estado;
	private ServicioRegistro servicio;
	private final SolicitudRegistro solicitud = new SolicitudRegistro(" Clinica-Lima ", " Clínica Lima ",
			"America/Lima", " CUENTA@Institucion.PE ", " clave-de-prueba ");

	@BeforeEach
	void preparar() {
		repositorio = mock(RepositorioRegistro.class);
		codificador = mock(PasswordEncoder.class);
		gestor = mock(PlatformTransactionManager.class);
		estado = new SimpleTransactionStatus();
		when(gestor.getTransaction(any(TransactionDefinition.class))).thenReturn(estado);
		when(codificador.encode(" clave-de-prueba ")).thenReturn("hash-de-prueba");
		servicio = new ServicioRegistro(repositorio, codificador, gestor);
	}

	@Test
	void calculaHashAntesDeAbrirTransaccionYPersisteIdsRelacionados() {
		RespuestaRegistro respuesta = servicio.registrar(solicitud);
		ArgumentCaptor<DatosRegistro> datos = ArgumentCaptor.forClass(DatosRegistro.class);
		InOrder orden = inOrder(codificador, gestor, repositorio);
		orden.verify(codificador).encode(" clave-de-prueba ");
		orden.verify(gestor).getTransaction(any(TransactionDefinition.class));
		orden.verify(repositorio).insertarInstitucion(eq(respuesta.idInstitucion()), datos.capture());
		orden.verify(repositorio).insertarCuenta(respuesta.idCuenta(), respuesta.idInstitucion(),
				"cuenta@institucion.pe", "hash-de-prueba");
		orden.verify(gestor).commit(estado);

		assertEquals("Clinica-Lima", respuesta.codigoInstitucion());
		assertEquals("Clínica Lima", respuesta.nombreInstitucion());
		assertEquals("cuenta@institucion.pe", datos.getValue().correo());
	}

	@Test
	void falloDeSegundoInsertRevierteTodaLaOperacion() {
		doThrow(new DataIntegrityViolationException("Fallo simulado"))
				.when(repositorio).insertarCuenta(any(UUID.class), any(UUID.class), any(), any());
		assertThrows(DataIntegrityViolationException.class, () -> servicio.registrar(solicitud));
		verify(gestor).rollback(estado);
		verify(gestor, never()).commit(any());
	}

	@Test
	void duplicadoNoInsertaCuentaYRevierteTransaccion() {
		doThrow(new InstitucionDuplicadaException())
				.when(repositorio).insertarInstitucion(any(UUID.class), any(DatosRegistro.class));
		assertThrows(InstitucionDuplicadaException.class, () -> servicio.registrar(solicitud));
		verify(repositorio, never()).insertarCuenta(any(), any(), any(), any());
		verify(gestor).rollback(estado);
	}

	@Test
	void falloDelHashNoAbreTransaccionNiPersisteDatos() {
		when(codificador.encode(" clave-de-prueba ")).thenThrow(new IllegalStateException("Fallo del codificador"));
		assertThrows(IllegalStateException.class, () -> servicio.registrar(solicitud));
		verifyNoInteractions(gestor, repositorio);
	}

	@Test
	void datosInvalidosNoCalculanHashNiAbrenTransaccion() {
		assertThrows(ValidacionRegistroException.class,
				() -> servicio.registrar(new SolicitudRegistro(null, null, null, null, null)));
		verifyNoInteractions(codificador, gestor, repositorio);
	}

}
