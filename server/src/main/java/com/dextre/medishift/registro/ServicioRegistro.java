package com.dextre.medishift.registro;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dextre.medishift.acceso.RepositorioAccesoMensual;

@Service
public class ServicioRegistro {

	private final RepositorioRegistro repositorio;
	private final RepositorioAccesoMensual accesos;
	private final PasswordEncoder codificador;
	private final TransactionTemplate transacciones;

	public ServicioRegistro(RepositorioRegistro repositorio, RepositorioAccesoMensual accesos, PasswordEncoder codificador,
			PlatformTransactionManager gestorTransacciones) {
		this.repositorio = repositorio;
		this.accesos = accesos;
		this.codificador = codificador;
		this.transacciones = new TransactionTemplate(gestorTransacciones);
	}

	public RespuestaRegistro registrar(SolicitudRegistro solicitud) {
		DatosRegistro datos = ValidadorRegistro.validar(solicitud);
		// El cálculo costoso del hash no mantiene abierta la transacción ni una conexión.
		String hashContrasena = codificador.encode(datos.contrasena());
		UUID idInstitucion = UUID.randomUUID();
		UUID idCuenta = UUID.randomUUID();

		return transacciones.execute(estado -> {
			repositorio.insertarInstitucion(idInstitucion, datos);
			repositorio.insertarCuenta(idCuenta, idInstitucion, datos.correo(), hashContrasena);
			// Solo se asigna la cuenta creadora de esta nueva institución.
			int rol = accesos.resolverRolAdministrador();
			accesos.asignarAdministrador(idCuenta, rol);
			accesos.registrarAltaInstitucion(idInstitucion, idCuenta);
			return new RespuestaRegistro(idInstitucion, idCuenta, datos.codigoInstitucion(),
					datos.nombreInstitucion(), datos.correo());
		});
	}

}
