package com.dextre.medishift.sesion;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dextre.medishift.registro.ValidacionRegistroException;
import com.dextre.medishift.sesion.RepositorioSesion.CuentaAutenticable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioSesionTests {

	private RepositorioSesion repositorio;
	private PasswordEncoder codificador;
	private ServicioSesion servicio;
	private RespuestaSesion perfil;
	private SolicitudSesion solicitud;

	@BeforeEach
	void preparar() {
		repositorio = mock(RepositorioSesion.class);
		codificador = mock(PasswordEncoder.class);
		when(codificador.encode("verificacion-interna-medishift")).thenReturn("hash-ficticio");
		servicio = new ServicioSesion(repositorio, codificador);
		perfil = new RespuestaSesion(UUID.randomUUID(), UUID.randomUUID(),
				"Clinica-Lima", "Clínica Lima", "cuenta@institucion.pe");
		solicitud = new SolicitudSesion(" Clinica-Lima ", " CUENTA@Institucion.PE ", " Clave exacta ");
	}

	@Test
	void autenticaContraHashGuardadoSinModificarLaContrasena() {
		when(repositorio.buscarCredenciales("Clinica-Lima", "cuenta@institucion.pe"))
				.thenReturn(Optional.of(new CuentaAutenticable(perfil, "hash-guardado")));
		when(codificador.matches(" Clave exacta ", "hash-guardado")).thenReturn(true);
		assertEquals(perfil, servicio.autenticar(solicitud));
		verify(codificador).matches(" Clave exacta ", "hash-guardado");
	}

	@Test
	void usuarioInexistenteHaceComprobacionFicticiaYDevuelve401Generico() {
		when(repositorio.buscarCredenciales("Clinica-Lima", "cuenta@institucion.pe"))
				.thenReturn(Optional.empty());
		assertThrows(AccesoNoAutorizadoException.class, () -> servicio.autenticar(solicitud));
		verify(codificador).matches(" Clave exacta ", "hash-ficticio");
	}

	@Test
	void contrasenaErroneaYHashDesconocidoDanMismoError() {
		when(repositorio.buscarCredenciales("Clinica-Lima", "cuenta@institucion.pe"))
				.thenReturn(Optional.of(new CuentaAutenticable(perfil, "hash-guardado")));
		assertThrows(AccesoNoAutorizadoException.class, () -> servicio.autenticar(solicitud));
		when(codificador.matches(" Clave exacta ", "hash-guardado"))
				.thenThrow(new IllegalArgumentException("Identificador desconocido"));
		assertThrows(AccesoNoAutorizadoException.class, () -> servicio.autenticar(solicitud));
		verify(codificador).matches(" Clave exacta ", "hash-ficticio");
	}

	@Test
	void entradaInvalidaNoConsultaBaseNiVerificaHash() {
		assertThrows(ValidacionRegistroException.class,
				() -> servicio.autenticar(new SolicitudSesion(null, null, null)));
		verifyNoInteractions(repositorio);
		verify(codificador, never()).matches(any(), any());
	}

	@Test
	void consultaPorDosUuidRevalidaCadaPeticion() {
		when(repositorio.buscarSesion(perfil.idInstitucion(), perfil.idCuenta())).thenReturn(Optional.of(perfil));
		assertEquals(Optional.of(perfil), servicio.consultar(perfil.idInstitucion(), perfil.idCuenta()));
		when(repositorio.buscarSesion(perfil.idInstitucion(), perfil.idCuenta())).thenReturn(Optional.empty());
		assertEquals(Optional.empty(), servicio.consultar(perfil.idInstitucion(), perfil.idCuenta()));
	}

}
