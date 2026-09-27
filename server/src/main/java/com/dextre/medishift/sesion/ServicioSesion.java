package com.dextre.medishift.sesion;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dextre.medishift.registro.DatosCredenciales;
import com.dextre.medishift.registro.ValidadorRegistro;
import com.dextre.medishift.registro.ValidacionRegistroException;
import com.dextre.medishift.sesion.RepositorioSesion.CuentaAutenticable;

@Service
public class ServicioSesion {

	private final RepositorioSesion repositorio;
	private final PasswordEncoder codificador;
	private final String hashFicticio;

	public ServicioSesion(RepositorioSesion repositorio, PasswordEncoder codificador) {
		this.repositorio = repositorio;
		this.codificador = codificador;
		this.hashFicticio = codificador.encode("verificacion-interna-medishift");
	}

	public RespuestaSesion autenticar(SolicitudSesion solicitud) {
		if (solicitud == null) {
			throw new ValidacionRegistroException(Map.of("sesion", "Envía un objeto JSON de acceso."));
		}
		DatosCredenciales datos = ValidadorRegistro.validarCredenciales(
				solicitud.codigoInstitucion(), solicitud.correo(), solicitud.contrasena());
		Optional<CuentaAutenticable> cuenta = repositorio.buscarCredenciales(datos.codigoInstitucion(), datos.correo());
		if (cuenta.isEmpty()) {
			codificador.matches(datos.contrasena(), hashFicticio);
			throw new AccesoNoAutorizadoException();
		}

		try {
			if (codificador.matches(datos.contrasena(), cuenta.get().hashContrasena())) {
				return cuenta.get().perfil();
			}
		} catch (IllegalArgumentException excepcion) {
			// Un hash antiguo o no admitido tampoco revela si la cuenta existe.
			codificador.matches(datos.contrasena(), hashFicticio);
		}
		throw new AccesoNoAutorizadoException();
	}

	public Optional<RespuestaSesion> consultar(UUID idInstitucion, UUID idCuenta) {
		return repositorio.buscarSesion(idInstitucion, idCuenta);
	}

}
