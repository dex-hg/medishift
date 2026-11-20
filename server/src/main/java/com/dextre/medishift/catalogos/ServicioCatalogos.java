package com.dextre.medishift.catalogos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoDoctores;
import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.acceso.ValidadorAccesoDoctores.Solicitud;
import com.dextre.medishift.catalogos.DatosCatalogos.CambioEtiqueta;
import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;
import com.dextre.medishift.catalogos.DatosCatalogos.Etiqueta;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion.Alcance;
import com.dextre.medishift.programacion.RevisionesProgramacion;

import tools.jackson.databind.json.JsonMapper;

@Service
public class ServicioCatalogos {

	private final RepositorioProfesionales profesionales;
	private final RepositorioConsultorios consultorios;
	private final RepositorioEspecialidades especialidades;
	private final RepositorioRecursosProgramacion programacion;
	private final ServicioAccesoMensual permisos;
	private final ServicioAccesoDoctores accesoDoctores;
	private final JsonMapper json;

	public ServicioCatalogos(RepositorioProfesionales profesionales, RepositorioConsultorios consultorios,
			RepositorioEspecialidades especialidades, RepositorioRecursosProgramacion programacion,
			ServicioAccesoMensual permisos, ServicioAccesoDoctores accesoDoctores, JsonMapper json) {
		this.profesionales = profesionales;
		this.consultorios = consultorios;
		this.especialidades = especialidades;
		this.programacion = programacion;
		this.permisos = permisos;
		this.accesoDoctores = accesoDoctores;
		this.json = json;
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
		UUID especialidad = especialidades.resolver(institucion, profesional.especialidad());
		profesionales.insertar(institucion, identificador, profesional);
		profesionales.asignarEspecialidad(institucion, identificador, especialidad);
		return buscarProfesional(institucion, identificador);
	}

	@Transactional
	public Profesional actualizarProfesional(UUID institucion, UUID identificador, Profesional profesional) {
		especialidades.bloquearInstitucion(institucion);
		profesionales.bloquear(institucion, identificador);
		profesionales.verificarDuplicados(institucion, identificador, profesional);
		UUID especialidad = especialidades.resolver(institucion, profesional.especialidad());
		profesionales.actualizar(institucion, identificador, profesional);
		profesionales.asignarEspecialidad(institucion, identificador, especialidad);
		programacion.exigirTurnosCompatibles(institucion, Alcance.PROFESIONAL, identificador, "estado");
		return buscarProfesional(institucion, identificador);
	}

	@Transactional
	public Profesional guardarProfesional(UUID institucion, UUID actor, UUID identificador, Profesional datos,
			List<UUID> etiquetas, String revision, Solicitud credenciales) {
		prepararEdicion(institucion, actor);
		Profesional anterior = identificador == null ? null : buscarProfesional(institucion, identificador);
		if (anterior != null) RevisionesProgramacion.comprobar(revision, anterior.revision());
		List<UUID> etiquetasSeleccionadas = etiquetas == null
				? List.of(especialidades.resolver(institucion, datos.especialidad())) : etiquetas;
		especialidades.exigirActivas(institucion, etiquetasSeleccionadas);
		UUID profesional = identificador == null ? UUID.randomUUID() : identificador;
		profesionales.verificarDuplicados(institucion, profesional, datos);
		if (anterior == null) profesionales.insertar(institucion, profesional, datos);
		else profesionales.actualizar(institucion, profesional, datos);
		profesionales.guardarEtiquetas(institucion, profesional, etiquetasSeleccionadas);
		if (anterior != null) {
			programacion.exigirTurnosCompatibles(institucion, Alcance.PROFESIONAL, profesional, "idsEtiquetas");
		} else if (credenciales != null) {
			accesoDoctores.crearAccesoInicial(institucion, actor, profesional, credenciales);
		}
		Profesional resultado = buscarProfesional(institucion, profesional);
		auditar(institucion, actor, "professional", profesional, captura(anterior), captura(resultado));
		return resultado;
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
		UUID especialidad = consultorio.especialidad().isEmpty() ? null : especialidades.resolver(institucion, consultorio.especialidad());
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
		UUID especialidad = consultorio.especialidad().isEmpty() ? null : especialidades.resolver(institucion, consultorio.especialidad());
		consultorios.actualizar(institucion, identificador, consultorio);
		consultorios.asignarEspecialidad(institucion, identificador, especialidad);
		programacion.exigirTurnosCompatibles(institucion, Alcance.CONSULTORIO, identificador, "especialidad");
		return buscarConsultorio(institucion, identificador);
	}

	@Transactional
	public Consultorio guardarConsultorio(UUID institucion, UUID actor, UUID identificador, Consultorio datos,
			List<UUID> etiquetas, String revision) {
		prepararEdicion(institucion, actor);
		Consultorio anterior = identificador == null ? null : buscarConsultorio(institucion, identificador);
		if (anterior != null) RevisionesProgramacion.comprobar(revision, anterior.revision());
		especialidades.exigirActivas(institucion, etiquetas);
		UUID consultorio = identificador == null ? UUID.randomUUID() : identificador;
		consultorios.verificarDuplicados(institucion, consultorio, datos);
		Consultorio datosSala = new Consultorio(consultorio, datos.codigo(), datos.nombre(), datos.ubicacion(),
				"", datos.estado(), etiquetas.isEmpty(), List.of());
		if (anterior == null) consultorios.insertar(institucion, consultorio, datosSala);
		else consultorios.actualizar(institucion, consultorio, datosSala);
		consultorios.guardarEtiquetas(institucion, consultorio, etiquetas);
		if (anterior != null) {
			programacion.exigirTurnosCompatibles(institucion, Alcance.CONSULTORIO, consultorio, "idsEtiquetas");
			programacion.exigirConsultorioCompatible(institucion, consultorio, "idsEtiquetas");
		}
		Consultorio resultado = buscarConsultorio(institucion, consultorio);
		auditar(institucion, actor, "room", consultorio, anterior, resultado);
		return resultado;
	}

	@Transactional
	public void eliminarConsultorio(UUID institucion, UUID identificador) {
		especialidades.bloquearInstitucion(institucion);
		consultorios.bloquear(institucion, identificador);
		consultorios.eliminar(institucion, identificador);
	}

	@Transactional(readOnly = true)
	public List<Especialidad> listarEspecialidades(UUID institucion) {
		return especialidades.listar(institucion);
	}

	@Transactional
	public Especialidad crearEspecialidad(UUID institucion, String nombre) {
		return especialidades.crear(institucion, nombre);
	}

	@Transactional(readOnly = true)
	public List<Etiqueta> listarEtiquetas(UUID institucion) {
		return especialidades.listarEtiquetas(institucion);
	}

	@Transactional
	public Etiqueta crearEtiqueta(UUID institucion, UUID actor, String nombre) {
		prepararEdicion(institucion, actor);
		Etiqueta etiqueta = especialidades.crearEtiqueta(institucion, nombre);
		auditar(institucion, actor, "specialty", etiqueta.id(), null, etiqueta);
		return etiqueta;
	}

	@Transactional
	public Etiqueta actualizarEtiqueta(UUID institucion, UUID actor, UUID identificador, String revision, CambioEtiqueta cambio) {
		prepararEdicion(institucion, actor);
		Etiqueta anterior = especialidades.buscarEtiqueta(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, anterior.revision());
		Etiqueta etiqueta = especialidades.cambiar(institucion, identificador, cambio);
		if (anterior.activa() && !etiqueta.activa()) {
			programacion.exigirTurnosCompatibles(institucion, Alcance.ETIQUETA, identificador, "activa");
			programacion.exigirEtiquetaSinCobertura(institucion, identificador);
		}
		auditar(institucion, actor, "specialty", identificador, anterior, etiqueta);
		return etiqueta;
	}

	private void prepararEdicion(UUID institucion, UUID actor) {
		especialidades.bloquearInstitucion(institucion);
		permisos.exigirAdministrador(institucion, actor);
	}

	private Map<String, Object> captura(Profesional profesional) {
		return profesional == null ? null : Map.of("estado", profesional.estado(), "etiquetas", profesional.etiquetas());
	}

	private void auditar(UUID institucion, UUID actor, String entidad, UUID id, Object antes, Object despues) {
		especialidades.auditar(institucion, actor, entidad, id, antes == null ? "create" : "update",
				antes == null ? null : json.writeValueAsString(antes), json.writeValueAsString(despues));
	}

}
