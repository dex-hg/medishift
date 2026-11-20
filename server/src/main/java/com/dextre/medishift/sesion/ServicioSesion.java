package com.dextre.medishift.sesion;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dextre.medishift.registro.DatosCredenciales;
import com.dextre.medishift.registro.ValidadorRegistro;
import com.dextre.medishift.registro.ValidacionRegistroException;
import com.dextre.medishift.seguridad.LimitadorSolicitudes;
import com.dextre.medishift.seguridad.LimitadorSolicitudes.Operacion;
import com.dextre.medishift.sesion.RepositorioSesion.CuentaAutenticable;

@Service
public class ServicioSesion {

	private final RepositorioSesion repositorio;
	private final PasswordEncoder codificador;
	private final LimitadorSolicitudes limites;
	private final String hashFicticio;

	public ServicioSesion(RepositorioSesion repositorio, PasswordEncoder codificador, LimitadorSolicitudes limites) {
		this.repositorio = repositorio;
		this.codificador = codificador;
		this.limites = limites;
		this.hashFicticio = codificador.encode("verificacion-interna-medishift");
	}

	public RespuestaSesion autenticar(SolicitudSesion solicitud, String direccionRemota) {
		if (solicitud == null) {
			throw new ValidacionRegistroException(Map.of("sesion", "Envía un objeto JSON de acceso."));
		}
		DatosCredenciales datos = ValidadorRegistro.validarCredenciales(
				solicitud.codigoInstitucion(), solicitud.correo(), solicitud.contrasena());
		var intento = limites.reservar(Operacion.SESION_PERSONAL, direccionRemota,
				datos.codigoInstitucion() + '\0' + datos.correo());
		try {
			RespuestaSesion perfil = verificarCredenciales(datos);
			limites.liberarIdentidad(intento);
			return perfil;
		} catch (DataAccessException fallo) {
			// Un fallo de infraestructura no se presenta como contraseña incorrecta.
			limites.liberarIdentidad(intento);
			throw fallo;
		}
	}

	private RespuestaSesion verificarCredenciales(DatosCredenciales datos) {
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
