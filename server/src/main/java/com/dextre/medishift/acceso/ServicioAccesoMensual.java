package com.dextre.medishift.acceso;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;

@Service
public class ServicioAccesoMensual {

	private final RepositorioAccesoMensual accesos;
	private final RepositorioEspecialidades exclusiones;

	public ServicioAccesoMensual(RepositorioAccesoMensual accesos, RepositorioEspecialidades exclusiones) {
		this.accesos = accesos;
		this.exclusiones = exclusiones;
	}

	/** Se consulta PostgreSQL en cada operación, incluso si la sesión sigue vigente. */
	@Transactional(readOnly = true)
	public void exigirAdministrador(UUID institucion, UUID actor) {
		if (institucion == null || actor == null || !accesos.administradorActivo(institucion, actor)) {
			throw CatalogoException.accesoDenegado();
		}
	}

	/** Solo para la inicialización local explícita de una cuenta existente. */
	@Transactional
	public boolean inicializarAdministrador(UUID institucion, UUID cuenta) {
		if (institucion == null || cuenta == null) {
			throw CatalogoException.accesoDenegado();
		}
		exclusiones.bloquearInstitucion(institucion);
		if (!accesos.cuentaActiva(institucion, cuenta)) {
			throw CatalogoException.accesoDenegado();
		}
		if (accesos.administradorActivo(institucion, cuenta)) {
			return false;
		}
		if (accesos.existeAdministradorActivo(institucion)) {
			throw CatalogoException.conflicto("administrador",
					"La institución ya tiene un administrador activo. La inicialización no permite añadir otro.");
		}
		int rol = accesos.resolverRolAdministrador();
		accesos.asignarAdministrador(cuenta, rol);
		accesos.registrarInicializacion(institucion, cuenta);
		return true;
	}
}
