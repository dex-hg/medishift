package com.dextre.medishift.catalogos;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;
import com.dextre.medishift.programacion.ReglasProgramacion;

@Service
public class ServicioCatalogos {

	private final RepositorioProfesionales profesionales;
	private final RepositorioConsultorios consultorios;
	private final RepositorioEspecialidades especialidades;
	private final ReglasProgramacion programacion;

	public ServicioCatalogos(RepositorioProfesionales profesionales, RepositorioConsultorios consultorios,
			RepositorioEspecialidades especialidades, ReglasProgramacion programacion) {
		this.profesionales = profesionales;
		this.consultorios = consultorios;
		this.especialidades = especialidades;
		this.programacion = programacion;
	}

	@Transactional(readOnly = true)
	public List<Profesional> listarProfesionales(UUID institucion) {
		return profesionales.listar(institucion);
	}

	@Transactional(readOnly = true)
	public Profesional buscarProfesional(UUID institucion, UUID identificador) {
		return profesionales.buscar(institucion, identificador).orElseThrow(CatalogoException::noEncontrado);
	}

	@Transactional
	public Profesional crearProfesional(UUID institucion, Profesional profesional) {
		especialidades.bloquearInstitucion(institucion);
		UUID identificador = UUID.randomUUID();
		profesionales.verificarDuplicados(institucion, identificador, profesional);
		UUID especialidad = especialidades.resolver(profesional.especialidad());
		profesionales.insertar(institucion, identificador, profesional);
		profesionales.asignarEspecialidad(institucion, identificador, especialidad);
		return buscarProfesional(institucion, identificador);
	}

	@Transactional
	public Profesional actualizarProfesional(UUID institucion, UUID identificador, Profesional profesional) {
		especialidades.bloquearInstitucion(institucion);
		profesionales.bloquear(institucion, identificador);
		profesionales.verificarDuplicados(institucion, identificador, profesional);
		UUID especialidad = especialidades.resolver(profesional.especialidad());
		profesionales.actualizar(institucion, identificador, profesional);
		profesionales.asignarEspecialidad(institucion, identificador, especialidad);
		programacion.validarCatalogosFuturos(institucion);
		return buscarProfesional(institucion, identificador);
	}

	@Transactional
	public void eliminarProfesional(UUID institucion, UUID identificador) {
		especialidades.bloquearInstitucion(institucion);
		profesionales.bloquear(institucion, identificador);
		profesionales.eliminar(institucion, identificador);
	}

	@Transactional(readOnly = true)
	public List<Consultorio> listarConsultorios(UUID institucion) {
		return consultorios.listar(institucion);
	}

	@Transactional(readOnly = true)
	public Consultorio buscarConsultorio(UUID institucion, UUID identificador) {
		return consultorios.buscar(institucion, identificador).orElseThrow(CatalogoException::noEncontrado);
	}

	@Transactional
	public Consultorio crearConsultorio(UUID institucion, Consultorio consultorio) {
		especialidades.bloquearInstitucion(institucion);
		UUID identificador = UUID.randomUUID();
		consultorios.verificarDuplicados(institucion, identificador, consultorio);
		UUID especialidad = consultorio.especialidad().isEmpty() ? null : especialidades.resolver(consultorio.especialidad());
		consultorios.insertar(institucion, identificador, consultorio);
		consultorios.asignarEspecialidad(institucion, identificador, especialidad);
		return buscarConsultorio(institucion, identificador);
	}

	@Transactional
	public Consultorio actualizarConsultorio(UUID institucion, UUID identificador, Consultorio consultorio) {
		especialidades.bloquearInstitucion(institucion);
		consultorios.bloquear(institucion, identificador);
		consultorios.verificarDuplicados(institucion, identificador, consultorio);
		consultorios.verificarCambioEspecialidad(institucion, identificador, consultorio.especialidad());
		UUID especialidad = consultorio.especialidad().isEmpty() ? null : especialidades.resolver(consultorio.especialidad());
		consultorios.actualizar(institucion, identificador, consultorio);
		consultorios.asignarEspecialidad(institucion, identificador, especialidad);
		programacion.validarCatalogosFuturos(institucion);
		return buscarConsultorio(institucion, identificador);
	}

	@Transactional
	public void eliminarConsultorio(UUID institucion, UUID identificador) {
		especialidades.bloquearInstitucion(institucion);
		consultorios.bloquear(institucion, identificador);
		consultorios.eliminar(institucion, identificador);
	}

	@Transactional(readOnly = true)
	public List<Especialidad> listarEspecialidades() {
		return especialidades.listar();
	}

}
