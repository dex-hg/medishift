package com.dextre.medishift.acceso;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.DatosAccesoDoctores.AccesoDoctor;
import com.dextre.medishift.acceso.DatosAccesoDoctores.Vinculo;
import com.dextre.medishift.acceso.ValidadorAccesoDoctores.Solicitud;
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

	@Transactional(propagation = Propagation.MANDATORY)
	public void crearAccesoInicial(UUID institucion, UUID actor, UUID profesional, Solicitud solicitud) {
		permisos.exigirAdministrador(institucion, actor);
		Vinculo actual = repositorio.buscar(institucion, profesional);
		exigirProfesionalActivo(actual, "estado");
		if (actual.idCuenta() != null) {
			throw CatalogoException.conflicto("correoAcceso", "El profesional ya tiene una cuenta de acceso.");
		}
		crearCuenta(institucion, actor, profesional, solicitud, "correoAcceso");
	}

	@Transactional
	public AccesoDoctor guardar(UUID institucion, UUID actor, UUID profesional, String revision, Map<String, Object> datos) {
		exclusiones.bloquearInstitucion(institucion);
		permisos.exigirAdministrador(institucion, actor);
		Vinculo actual = repositorio.buscarParaEditar(institucion, profesional);
		if (!actual.vista(profesional).revision().equals(revision)) {
			throw CatalogoException.conflicto("revision", "El acceso cambió en otra ventana. Recarga antes de guardar.");
		}
		Solicitud solicitud = ValidadorAccesoDoctores.validar(datos, actual.idCuenta() == null);
		if (Boolean.TRUE.equals(solicitud.habilitado())) exigirProfesionalActivo(actual, "habilitado");
		if (actual.idCuenta() == null) {
			crearCuenta(institucion, actor, profesional, solicitud, "correo");
		} else {
			actualizarCuenta(institucion, actor, profesional, actual, solicitud);
		}
		return repositorio.buscar(institucion, profesional).vista(profesional);
	}

	private void crearCuenta(UUID institucion, UUID actor, UUID profesional, Solicitud solicitud, String campoCorreo) {
		UUID cuenta = repositorio.crearCuenta(institucion, profesional, solicitud.correo(),
				codificador.encode(solicitud.contrasena()), campoCorreo);
		repositorio.cambiarRol(cuenta, true);
		repositorio.auditar(institucion, actor, profesional, true);
	}

	private void actualizarCuenta(UUID institucion, UUID actor, UUID profesional, Vinculo actual, Solicitud solicitud) {
		boolean cambiaCorreo = !actual.correo().equalsIgnoreCase(solicitud.correo());
		boolean cambiaContrasena = !solicitud.contrasena().isEmpty();
		if (cambiaCorreo || cambiaContrasena) {
			if (actual.tieneOtrosRoles()) {
				throw CatalogoException.credencialesProtegidas();
			}
			String hash = cambiaContrasena ? codificador.encode(solicitud.contrasena()) : null;
			String correo = cambiaCorreo ? solicitud.correo() : actual.correo();
			repositorio.cambiarCredenciales(institucion, actual.idCuenta(), correo, hash);
			repositorio.auditarCredenciales(institucion, actor, profesional, cambiaCorreo, cambiaContrasena);
		}
		if (solicitud.habilitado() != null) {
			if (solicitud.habilitado() && !"active".equals(actual.estadoCuenta())) {
				throw CatalogoException.conflicto("habilitado", "La cuenta está bloqueada o inactiva.");
			}
			repositorio.cambiarRol(actual.idCuenta(), solicitud.habilitado());
			repositorio.auditar(institucion, actor, profesional, solicitud.habilitado());
		}
	}

	private void exigirProfesionalActivo(Vinculo actual, String campo) {
		if (!"active".equals(actual.estadoProfesional())) {
			throw CatalogoException.conflicto(campo, "Activa primero al profesional para habilitar su acceso.");
		}
	}
}
