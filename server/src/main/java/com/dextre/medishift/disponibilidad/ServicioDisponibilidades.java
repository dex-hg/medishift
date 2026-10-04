package com.dextre.medishift.disponibilidad;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.catalogos.RepositorioProfesionales;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.SolicitudDisponibilidad;
import com.dextre.medishift.programacion.ReglasProgramacion;

@Service
public class ServicioDisponibilidades {

	private final RepositorioDisponibilidades disponibilidades;
	private final RepositorioProfesionales profesionales;
	private final RepositorioEspecialidades especialidades;
	private final ReglasProgramacion reglas;

	public ServicioDisponibilidades(RepositorioDisponibilidades disponibilidades,
			RepositorioProfesionales profesionales, RepositorioEspecialidades especialidades, ReglasProgramacion reglas) {
		this.disponibilidades = disponibilidades;
		this.profesionales = profesionales;
		this.especialidades = especialidades;
		this.reglas = reglas;
	}

	@Transactional(readOnly = true)
	public List<Disponibilidad> listar(UUID institucion) {
		return disponibilidades.listar(institucion);
	}

	@Transactional(readOnly = true)
	public Disponibilidad buscar(UUID institucion, UUID identificador) {
		return disponibilidades.buscar(institucion, identificador).orElseThrow(CatalogoException::noEncontrado);
	}

	@Transactional
	public Disponibilidad crear(UUID institucion, SolicitudDisponibilidad solicitud) {
		especialidades.bloquearInstitucion(institucion);
		validarProfesionalActivo(institucion, solicitud.idProfesional());
		UUID identificador = UUID.randomUUID();
		disponibilidades.verificarCruces(institucion, identificador, solicitud);
		disponibilidades.insertar(institucion, identificador, solicitud);
		reglas.validarCambioDisponibilidades(institucion, solicitud.idProfesional());
		return buscar(institucion, identificador);
	}

	@Transactional
	public Disponibilidad actualizar(UUID institucion, UUID identificador, String revision,
			SolicitudDisponibilidad solicitud) {
		especialidades.bloquearInstitucion(institucion);
		Disponibilidad actual = buscar(institucion, identificador);
		RevisionDisponibilidades.exigirActual(revision, actual);
		validarProfesionalActivo(institucion, solicitud.idProfesional());
		disponibilidades.verificarCruces(institucion, identificador, solicitud);
		disponibilidades.actualizar(institucion, identificador, solicitud);
		reglas.validarCambioDisponibilidades(institucion, actual.idProfesional());
		if (!actual.idProfesional().equals(solicitud.idProfesional())) {
			reglas.validarCambioDisponibilidades(institucion, solicitud.idProfesional());
		}
		return buscar(institucion, identificador);
	}

	@Transactional
	public void eliminar(UUID institucion, UUID identificador, String revision) {
		especialidades.bloquearInstitucion(institucion);
		Disponibilidad actual = buscar(institucion, identificador);
		RevisionDisponibilidades.exigirActual(revision, actual);
		disponibilidades.eliminar(institucion, identificador);
		reglas.validarCambioDisponibilidades(institucion, actual.idProfesional());
	}

	private void validarProfesionalActivo(UUID institucion, UUID identificador) {
		var profesional = profesionales.buscar(institucion, identificador).orElseThrow(CatalogoException::noEncontrado);
		if (!"Activo".equals(profesional.estado())) {
			throw CatalogoException.conflicto("idProfesional", "Selecciona un profesional activo de la institución.");
		}
	}

}
