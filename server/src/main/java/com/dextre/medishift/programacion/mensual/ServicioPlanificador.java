package com.dextre.medishift.programacion.mensual;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.feriados.ServicioFeriados;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.DatosCatalogos.Etiqueta;
import com.dextre.medishift.catalogos.RepositorioConsultorios;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.ReglasCargaProgramacion;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion.Alcance;
import com.dextre.medishift.programacion.RepositorioRestriccionesProgramacion;
import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.dextre.medishift.programacion.TiempoProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.Area;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.CalendarioMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.ConsultorioArea;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.RegistroTurno;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudAsignacion;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudConsultorio;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudArea;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudUnidad;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.TurnoMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;

@Service
public class ServicioPlanificador {

	private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
	private final ServicioAccesoMensual acceso;
	private final RepositorioPlanesMensuales planes;
	private final RepositorioPlanificador calendario;
	private final RepositorioAreasPlanificacion unidades;
	private final RepositorioConsultorios consultorios;
	private final RepositorioProgramacion horarios;
	private final RepositorioRecursosProgramacion recursos;
	private final RepositorioRestriccionesProgramacion restricciones;
	private final RepositorioEspecialidades exclusiones;
	private final ReglasCargaProgramacion carga;
	private final ServicioFeriados feriados;
	private final com.dextre.medishift.atencion.RepositorioProteccionCitas citas;

	public ServicioPlanificador(ServicioAccesoMensual acceso, RepositorioPlanesMensuales planes,
			RepositorioPlanificador calendario, RepositorioAreasPlanificacion unidades, RepositorioConsultorios consultorios,
			RepositorioProgramacion horarios, RepositorioRecursosProgramacion recursos,
			RepositorioRestriccionesProgramacion restricciones, RepositorioEspecialidades exclusiones,
			ReglasCargaProgramacion carga, ServicioFeriados feriados,
			com.dextre.medishift.atencion.RepositorioProteccionCitas citas) {
		this.acceso = acceso;
		this.planes = planes;
		this.calendario = calendario;
		this.unidades = unidades;
		this.consultorios = consultorios;
		this.horarios = horarios;
		this.recursos = recursos;
		this.restricciones = restricciones;
		this.exclusiones = exclusiones;
		this.carga = carga;
		this.feriados = feriados;
		this.citas = citas;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public CalendarioMensual consultar(UUID institucion, UUID actor, PeriodoMensual periodo) {
		var permisos = acceso.consultarProgramacion(institucion, actor);
		planes.verificarEsquema();
		calendario.verificarEsquema();
		ZoneId zona = horarios.zona(institucion);
		List<Area> areas = unidades.listar(institucion);
		List<PlanMensual> cabeceras = areas.stream().flatMap(area -> planes.planes(institucion, area.id(), periodo).stream()).toList();
		return new CalendarioMensual(zona.getId(), areas, calendario.consultorios(institucion),
				recursos.profesionales(institucion).stream()
						.filter(persona -> permisos.esAdministrador() || persona.id().equals(permisos.idProfesional())).toList(), cabeceras,
				calendario.turnos(institucion, periodo).stream()
						.filter(registro -> permisos.esAdministrador() || registro.turno().profesional().equals(permisos.idProfesional()))
						.map(registro -> vista(registro, zona)).toList(),
				exclusiones.listarEtiquetas(institucion).stream().filter(Etiqueta::activa).toList(),
				feriados.consultar(institucion, zona.getId(), periodo),
				carga.politica(institucion), permisos, calendario.ocupaciones(institucion,
						periodo.inicio().atStartOfDay(zona).toInstant(), periodo.inicio().plusMonths(1).atStartOfDay(zona).toInstant()));
	}

	@Transactional
	public Area crearArea(UUID institucion, UUID actor, SolicitudArea solicitud) {
		preparar(institucion, actor, true);
		exclusiones.exigirActivas(institucion, solicitud.idsEtiquetas());
		var unidad = planes.crearUnidad(institucion, new SolicitudUnidad(solicitud.codigo(), solicitud.nombre(), "Servicio"));
		unidades.guardar(institucion, unidad.id(), solicitud);
		unidades.auditar(institucion, actor, unidad.id(), null);
		return unidades.buscar(institucion, unidad.id());
	}

	@Transactional
	public Area configurarArea(UUID institucion, UUID actor, UUID identificador, UUID especialidad) {
		preparar(institucion, actor, true);
		Area anterior = validarArea(institucion, identificador);
		if (anterior.etiquetas().size() > 1) {
			throw CatalogoException.conflicto("idEspecialidad", "Edita el conjunto de etiquetas desde la configuración del área.");
		}
		exclusiones.buscar(institucion, especialidad);
		return guardarArea(institucion, actor, anterior,
				new SolicitudArea(null, anterior.nombre(), anterior.idAreaPadre(), List.of(especialidad), anterior.estado()),
				"idEspecialidad");
	}

	@Transactional
	public Area actualizarArea(UUID institucion, UUID actor, UUID identificador, String revision, SolicitudArea solicitud) {
		preparar(institucion, actor, true);
		Area anterior = unidades.buscar(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, anterior.revision());
		exclusiones.exigirActivas(institucion, solicitud.idsEtiquetas());
		return guardarArea(institucion, actor, anterior, solicitud, "idsEtiquetas");
	}

	private Area guardarArea(UUID institucion, UUID actor, Area anterior, SolicitudArea solicitud, String campo) {
		UUID area = anterior.id();
		String antes = unidades.captura(institucion, area);
		unidades.guardar(institucion, area, solicitud);
		if (!new HashSet<>(anterior.idsEtiquetas()).equals(new HashSet<>(solicitud.idsEtiquetas()))
				|| !anterior.estado().equals(solicitud.estado())) {
			recursos.exigirTurnosCompatibles(institucion, Alcance.AREA, area,
					anterior.estado().equals(solicitud.estado()) ? campo : "estado");
			if (!solicitud.idsEtiquetas().isEmpty()) recursos.exigirConsultoriosDelAreaCompatibles(institucion, area, campo);
		}
		unidades.auditar(institucion, actor, area, antes);
		return unidades.buscar(institucion, area);
	}

	@Transactional
	public ConsultorioArea crearConsultorio(UUID institucion, UUID actor, SolicitudConsultorio solicitud) {
		preparar(institucion, actor, true);
		Area area = exigirEtiquetasArea(institucion, solicitud.idArea());
		exclusiones.exigirActivas(institucion, solicitud.idsEtiquetas());
		if (!area.idsEtiquetas().containsAll(solicitud.idsEtiquetas())) {
			throw CatalogoException.conflicto("idsEtiquetas", "Selecciona etiquetas admitidas por esta unidad.");
		}
		UUID consultorio = calendario.crearConsultorio(institucion, solicitud);
		consultorios.guardarEtiquetas(institucion, consultorio, solicitud.idsEtiquetas());
		calendario.auditar(institucion, actor, consultorio, true, "create", null,
				calendario.captura(institucion, consultorio, true));
		return calendario.buscarConsultorio(institucion, consultorio);
	}

	@Transactional
	public ConsultorioArea vincularConsultorio(UUID institucion, UUID actor, UUID identificador, UUID area) {
		preparar(institucion, actor, true);
		exigirEtiquetasArea(institucion, area);
		calendario.buscarConsultorio(institucion, identificador);
		String antes = calendario.captura(institucion, identificador, true);
		calendario.vincularConsultorio(institucion, identificador, area);
		recursos.exigirTurnosCompatibles(institucion, Alcance.CONSULTORIO, identificador, "idConsultorio");
		recursos.exigirConsultorioCompatible(institucion, identificador, "idConsultorio");
		calendario.auditar(institucion, actor, identificador, true, "update", antes,
				calendario.captura(institucion, identificador, true));
		return calendario.buscarConsultorio(institucion, identificador);
	}

	@Transactional
	public TurnoMensual crearTurno(UUID institucion, UUID actor, SolicitudAsignacion solicitud) {
		prepararTurno(institucion, actor, solicitud.turno().idProfesional());
		ZoneId zona = horarios.zona(institucion);
		PlanMensual plan = borrador(institucion, actor, solicitud.idArea(), solicitud.turno().fecha());
		Turno nuevo = construir(UUID.randomUUID(), plan.id(), solicitud, zona);
		validar(institucion, solicitud.idArea(), nuevo, zona);
		calendario.insertar(institucion, nuevo);
		calendario.auditar(institucion, actor, nuevo.id(), false, "create", null,
				calendario.captura(institucion, nuevo.id(), false));
		return vista(calendario.buscarTurno(institucion, nuevo.id()), zona);
	}

	@Transactional
	public TurnoMensual actualizarTurno(UUID institucion, UUID actor, UUID identificador,
			String revision, SolicitudAsignacion solicitud) {
		prepararTurno(institucion, actor, solicitud.turno().idProfesional());
		RegistroTurno actual = calendario.buscarTurno(institucion, identificador);
		acceso.exigirTurnoPropio(institucion, actor, actual.turno().profesional());
		RevisionesProgramacion.comprobar(revision, revision(actual));
		exigirEditable(institucion, actual);
		ZoneId zona = horarios.zona(institucion);
		PlanMensual destino = borrador(institucion, actor, solicitud.idArea(), solicitud.turno().fecha());
		Turno nuevo = construir(identificador, destino.id(), solicitud, zona);
		validar(institucion, solicitud.idArea(), nuevo, zona);
		String antes = calendario.captura(institucion, identificador, false);
		if (!actual.turno().especialidad().equals(nuevo.especialidad())) citas.exigirRetiroPermitido(institucion, identificador);
		citas.exigirCambioPermitido(institucion, identificador, nuevo.profesional(), nuevo.consultorio(), nuevo.inicio(), nuevo.fin());
		calendario.actualizar(institucion, nuevo);
		calendario.auditar(institucion, actor, identificador, false, "update", antes,
				calendario.captura(institucion, identificador, false));
		return vista(calendario.buscarTurno(institucion, identificador), zona);
	}

	@Transactional
	public void eliminarTurno(UUID institucion, UUID actor, UUID identificador, String revision) {
		exclusiones.bloquearInstitucion(institucion);
		acceso.consultarProgramacion(institucion, actor);
		planes.verificarEsquema();
		calendario.verificarEsquema();
		RegistroTurno actual = calendario.buscarTurno(institucion, identificador);
		acceso.exigirTurnoPropio(institucion, actor, actual.turno().profesional());
		RevisionesProgramacion.comprobar(revision, revision(actual));
		exigirEditable(institucion, actual);
		String antes = calendario.captura(institucion, identificador, false);
		citas.exigirRetiroPermitido(institucion, identificador);
		if (!citas.conservarAlEliminar(institucion, identificador)) calendario.eliminar(institucion, identificador);
		calendario.auditar(institucion, actor, identificador, false, "delete", antes, null);
	}

	private void preparar(UUID institucion, UUID actor, boolean escritura) {
		if (escritura) { exclusiones.bloquearInstitucion(institucion); }
		acceso.exigirAdministrador(institucion, actor);
		planes.verificarEsquema();
		calendario.verificarEsquema();
	}

	private void prepararTurno(UUID institucion, UUID actor, UUID profesional) {
		exclusiones.bloquearInstitucion(institucion);
		acceso.exigirTurnoPropio(institucion, actor, profesional);
		planes.verificarEsquema();
		calendario.verificarEsquema();
	}

	private Area validarArea(UUID institucion, UUID area) {
		Area unidad = unidades.buscar(institucion, area);
		if (!"Activo".equals(unidad.estado())) {
			throw CatalogoException.conflicto("idArea", "El área está inactiva. Selecciona otra área.");
		}
		return unidad;
	}

	private PlanMensual borrador(UUID institucion, UUID actor, UUID area, LocalDate fecha) {
		validarArea(institucion, area);
		PeriodoMensual periodo = new PeriodoMensual(fecha.getYear(), fecha.getMonthValue());
		PlanMensual plan = planes.planes(institucion, area, periodo).stream()
				.filter(existente -> List.of("Borrador", "Pendiente", "Aprobado").contains(existente.estado())).findFirst().orElse(null);
		if (plan == null) {
			plan = planes.crearBorrador(institucion, actor, area, periodo);
			planes.registrarCreacion(institucion, actor, plan.id(), false);
		}
		if (!"Borrador".equals(plan.estado())) {
			throw CatalogoException.conflicto("estado", "El mes de esta área está " + plan.estado().toLowerCase(java.util.Locale.ROOT)
					+ ". Solo puedes asignar o mover turnos entre borradores.");
		}
		return plan;
	}

	private void exigirEditable(UUID institucion, RegistroTurno registro) {
		if (!"draft".equals(registro.estadoPlan()) || !"draft".equals(registro.turno().estado())) {
			throw CatalogoException.conflicto("estado", "Solo puedes modificar turnos de un mes en borrador.");
		}
		if (!registro.turno().inicio().isAfter(Instant.now()) || calendario.tieneAsistencia(institucion, registro.turno().id())) {
			throw CatalogoException.conflicto("fecha", "El turno ya comenzó o tiene asistencia registrada. Su historial debe conservarse.");
		}
	}

	private Turno construir(UUID identificador, UUID plan, SolicitudAsignacion solicitud, ZoneId zona) {
		var turno = solicitud.turno();
		return new Turno(identificador, plan, turno.idProfesional(), turno.idConsultorio(), turno.idEspecialidad(),
				TiempoProgramacion.resolver(turno.fecha(), turno.horaInicio(), zona),
				TiempoProgramacion.resolver(turno.fecha(), turno.horaFin(), zona), "draft", turno.observacion(), "", "", "");
	}

	/** La reaprobación conserva las asignaciones ejecutadas y vuelve a validar las pendientes. */
	void validarAprobacion(UUID institucion, PlanMensual plan) {
		validarArea(institucion, plan.idUnidad());
		var registros = calendario.turnos(institucion, new PeriodoMensual(plan.fechaInicio().getYear(), plan.fechaInicio().getMonthValue()))
				.stream().filter(registro -> registro.turno().horario().equals(plan.id()))
				.filter(registro -> !List.of("cancelled", "superseded").contains(registro.turno().estado())).toList();
		if (registros.isEmpty()) throw CatalogoException.conflicto("turnos", "Añade al menos un turno antes de aprobar el mes.");
		ZoneId zona = horarios.zona(institucion);
		Instant ahora = Instant.now();
		for (var registro : registros) {
			if (registro.turno().fin().isAfter(ahora)) validarAsignacion(institucion, plan.idUnidad(), registro.turno(), zona);
		}
		carga.validarCobertura(registros.stream().map(RegistroTurno::turno).toList(), horarios.requisitos(plan.id()));
	}

	private void validar(UUID institucion, UUID area, Turno turno, ZoneId zona) {
		if (!turno.inicio().isAfter(Instant.now())) {
			throw CatalogoException.conflicto("fecha", "Selecciona un turno que todavía no haya comenzado.");
		}
		validarAsignacion(institucion, area, turno, zona);
	}

	private void validarAsignacion(UUID institucion, UUID area, Turno turno, ZoneId zona) {
		Area seleccionada = exigirEtiquetasArea(institucion, area);
		if (!seleccionada.idsEtiquetas().contains(turno.especialidad())) {
			throw CatalogoException.conflicto("idEspecialidad", "El área o subárea no admite esa etiqueta.");
		}
		recursos.validar(institucion, turno, area);
		if (restricciones.tieneAusenciaAprobada(turno.profesional(), turno.inicio(), turno.fin())) {
			throw CatalogoException.conflicto("idProfesional", "El profesional tiene un permiso aprobado durante ese horario.");
		}
		if (restricciones.tieneBloqueoActivo(turno.consultorio(), turno.inicio(), turno.fin())) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio tiene un bloqueo activo durante ese horario.");
		}
		LocalDate lunes = TiempoProgramacion.inicioSemana(turno.inicio().atZone(zona).toLocalDate());
		List<Turno> vecinos = calendario.vecinos(institucion, lunes.atStartOfDay(zona).toInstant(),
				lunes.plusDays(7).atStartOfDay(zona).toInstant(), turno.id());
		for (Turno existente : vecinos) {
			if (TiempoProgramacion.seSuperponen(turno.inicio(), turno.fin(), existente.inicio(), existente.fin())) {
				if (turno.profesional().equals(existente.profesional())) {
					throw CatalogoException.conflicto("idProfesional", "El profesional ya tiene un turno de "
							+ existente.inicio().atZone(zona).format(FORMATO_HORA) + " a "
							+ existente.fin().atZone(zona).format(FORMATO_HORA) + " en " + existente.nombreConsultorio() + ".");
				}
				if (turno.consultorio().equals(existente.consultorio())) {
					throw CatalogoException.conflicto("idConsultorio", "El consultorio ya está ocupado por " + existente.nombreProfesional() + ".");
				}
			}
		}
		carga.validarHoras(institucion, List.of(turno), vecinos, lunes, zona);
	}

	private Area exigirEtiquetasArea(UUID institucion, UUID identificador) {
		Area area = validarArea(institucion, identificador);
		if (area.etiquetas().isEmpty()) {
			throw CatalogoException.conflicto("idArea", "Configura al menos una etiqueta para esta área o subárea.");
		}
		return area;
	}

	private TurnoMensual vista(RegistroTurno registro, ZoneId zona) {
		Turno turno = registro.turno();
		String estado = switch (turno.estado()) {
			case "draft" -> "Borrador";
			case "pending" -> "Pendiente";
			case "approved" -> "Aprobado";
			case "superseded" -> "Sustituido";
			default -> "Cancelado";
		};
		return new TurnoMensual(turno.id(), turno.horario(), registro.idArea(), turno.profesional(), turno.nombreProfesional(),
				turno.consultorio(), turno.nombreConsultorio(), turno.especialidad(), turno.nombreEspecialidad(),
				turno.inicio().atZone(zona).toLocalDate(), turno.inicio().atZone(zona).format(FORMATO_HORA),
				turno.fin().atZone(zona).format(FORMATO_HORA), estado, turno.observacion(), revision(registro),
				registro.asistencia(), registro.revisionAsistencia());
	}

	private String revision(RegistroTurno registro) {
		return RevisionesProgramacion.resumir(RevisionesProgramacion.deTurno(registro.turno()), registro.idArea(), registro.estadoPlan());
	}
}
