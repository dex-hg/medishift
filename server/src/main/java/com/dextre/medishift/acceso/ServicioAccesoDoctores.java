package com.dextre.medishift.acceso;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.DatosAccesoDoctores.AccesoDoctor;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;

@Service
public class ServicioAccesoDoctores {

	private final RepositorioAccesoDoctores repositorio;
	private final ServicioAccesoMensual permisos;
	private final RepositorioEspecialidades exclusiones;
	private final PasswordEncoder codificador;

	public ServicioAccesoDoctores(RepositorioAccesoDoctores repositorio, ServicioAccesoMensual permisos,
			RepositorioEspecialidades exclusiones, PasswordEncoder codificador) {
		this.repositorio = repositorio;
		this.permisos = permisos;
		this.exclusiones = exclusiones;
		this.codificador = codificador;
	}

	@Transactional(readOnly = true)
	public AccesoDoctor consultar(UUID institucion, UUID actor, UUID profesional) {
		permisos.exigirAdministrador(institucion, actor);
		return repositorio.buscar(institucion, profesional).vista(profesional);
	}

	@Transactional
	public AccesoDoctor guardar(UUID institucion, UUID actor, UUID profesional, String revision, Map<String, Object> datos) {
		exclusiones.bloquearInstitucion(institucion);
		permisos.exigirAdministrador(institucion, actor);
		var actual = repositorio.buscar(institucion, profesional);
		if (!actual.vista(profesional).revision().equals(revision)) {
			throw CatalogoException.conflicto("revision", "El acceso cambió en otra ventana. Recarga antes de guardar.");
		}
		var solicitud = ValidadorAccesoDoctores.validar(datos, actual.idCuenta() == null);
		boolean habilitado = solicitud.habilitado();
		String correo = solicitud.correo();
		String contrasena = solicitud.contrasena();
		if (habilitado && !"active".equals(actual.estadoProfesional())) {
			throw CatalogoException.conflicto("habilitado", "Activa primero al profesional para habilitar su acceso.");
		}
		UUID cuenta = actual.idCuenta();
		if (cuenta == null) {
			cuenta = repositorio.crearCuenta(institucion, profesional, correo, codificador.encode(contrasena));
		} else {
			if (!actual.correo().equalsIgnoreCase(correo) || !contrasena.isEmpty()) {
				throw CatalogoException.conflicto("correo", "Esta cuenta ya existe. Desde aquí solo puedes habilitar o revocar el rol de doctor.");
			}
			if (habilitado && !"active".equals(actual.estadoCuenta())) {
				throw CatalogoException.conflicto("habilitado", "La cuenta está bloqueada o inactiva.");
			}
		}
		repositorio.cambiarRol(cuenta, habilitado);
		repositorio.auditar(institucion, actor, profesional, habilitado);
		return repositorio.buscar(institucion, profesional).vista(profesional);
	}
}
