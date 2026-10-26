package com.dextre.medishift.programacion.mensual;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.feriados.ServicioFeriados;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.ReglasCargaProgramacion;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.RepositorioRecursosProgramacion;
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
	private final RepositorioProgramacion horarios;
	private final RepositorioRecursosProgramacion recursos;
	private final RepositorioRestriccionesProgramacion restricciones;
	private final RepositorioEspecialidades exclusiones;
	private final ReglasCargaProgramacion carga;
	private final ServicioFeriados feriados;

	public ServicioPlanificador(ServicioAccesoMensual acceso, RepositorioPlanesMensuales planes,
			RepositorioPlanificador calendario, RepositorioProgramacion horarios, RepositorioRecursosProgramacion recursos,
			RepositorioRestriccionesProgramacion restricciones, RepositorioEspecialidades exclusiones,
			ReglasCargaProgramacion carga, ServicioFeriados feriados) {
		this.acceso = acceso;
		this.planes = planes;
		this.calendario = calendario;
		this.horarios = horarios;
		this.recursos = recursos;
		this.restricciones = restricciones;
		this.exclusiones = exclusiones;
		this.carga = carga;
		this.feriados = feriados;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public CalendarioMensual consultar(UUID institucion, UUID actor, PeriodoMensual periodo) {
		preparar(institucion, actor, false);
		ZoneId zona = horarios.zona(institucion);
		List<Area> areas = calendario.areas(institucion);
		List<PlanMensual> cabeceras = areas.stream().flatMap(area -> planes.planes(institucion, area.id(), periodo).stream()).toList();
		return new CalendarioMensual(zona.getId(), areas, calendario.consultorios(institucion),
				recursos.listar(institucion).profesionales(), cabeceras,
				calendario.turnos(institucion, periodo).stream().map(registro -> vista(registro, zona)).toList(),
				exclusiones.listar(institucion).stream().map(especialidad -> new com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso(
						especialidad.id(), especialidad.nombre())).toList(), feriados.consultar(zona.getId(), periodo));
	}

	@Transactional
	public Area crearArea(UUID institucion, UUID actor, SolicitudArea solicitud) {
		preparar(institucion, actor, true);
		exclusiones.buscar(institucion, solicitud.idEspecialidad());
		var unidad = planes.crearUnidad(institucion, new SolicitudUnidad(solicitud.codigo(), solicitud.nombre(), "Servicio"));
		calendario.configurarArea(institucion, unidad.id(), solicitud.idEspecialidad());
		planes.registrarCreacion(institucion, actor, unidad.id(), true);
		return calendario.buscarArea(institucion, unidad.id());
	}

	@Transactional
	public Area configurarArea(UUID institucion, UUID actor, UUID identificador, UUID especialidad) {
		preparar(institucion, actor, true);
		validarArea(institucion, identificador);
		exclusiones.buscar(institucion, especialidad);
		String antes = calendario.capturaArea(institucion, identificador);
		calendario.configurarArea(institucion, identificador, especialidad);
		calendario.auditarArea(institucion, actor, identificador, antes);
		return calendario.buscarArea(institucion, identificador);
	}

	@Transactional
	public ConsultorioArea crearConsultorio(UUID institucion, UUID actor, SolicitudConsultorio solicitud) {
		preparar(institucion, actor, true);
		validarArea(institucion, solicitud.idArea());
		exigirEspecialidadArea(institucion, solicitud.idArea());
		ConsultorioArea consultorio = calendario.crearConsultorio(institucion, solicitud);
		calendario.auditar(institucion, actor, consultorio.id(), true, "create", null,
				calendario.captura(institucion, consultorio.id(), true));
		return consultorio;
	}

	@Transactional
	public ConsultorioArea vincularConsultorio(UUID institucion, UUID actor, UUID identificador, UUID area) {
		preparar(institucion, actor, true);
		validarArea(institucion, area);
		exigirEspecialidadArea(institucion, area);
		calendario.buscarConsultorio(institucion, identificador);
		String antes = calendario.captura(institucion, identificador, true);
		calendario.vincularConsultorio(institucion, identificador, area);
		calendario.auditar(institucion, actor, identificador, true, "update", antes,
				calendario.captura(institucion, identificador, true));
		return calendario.buscarConsultorio(institucion, identificador);
	}

	@Transactional
	public TurnoMensual crearTurno(UUID institucion, UUID actor, SolicitudAsignacion solicitud) {
		preparar(institucion, actor, true);
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
		preparar(institucion, actor, true);
		RegistroTurno actual = calendario.buscarTurno(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, revision(actual));
		exigirEditable(institucion, actual);
		ZoneId zona = horarios.zona(institucion);
		PlanMensual destino = borrador(institucion, actor, solicitud.idArea(), solicitud.turno().fecha());
		Turno nuevo = construir(identificador, destino.id(), solicitud, zona);
		validar(institucion, solicitud.idArea(), nuevo, zona);
		String antes = calendario.captura(institucion, identificador, false);
		calendario.actualizar(institucion, nuevo);
		calendario.auditar(institucion, actor, identificador, false, "update", antes,
				calendario.captura(institucion, identificador, false));
		return vista(calendario.buscarTurno(institucion, identificador), zona);
	}

	@Transactional
	public void eliminarTurno(UUID institucion, UUID actor, UUID identificador, String revision) {
		preparar(institucion, actor, true);
		RegistroTurno actual = calendario.buscarTurno(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, revision(actual));
		exigirEditable(institucion, actual);
		String antes = calendario.captura(institucion, identificador, false);
		calendario.eliminar(institucion, identificador);
		calendario.auditar(institucion, actor, identificador, false, "delete", antes, null);
	}

	private void preparar(UUID institucion, UUID actor, boolean escritura) {
		if (escritura) { exclusiones.bloquearInstitucion(institucion); }
		acceso.exigirAdministrador(institucion, actor);
		planes.verificarEsquema();
		calendario.verificarEsquema();
	}

	private void validarArea(UUID institucion, UUID area) {
		if (!"Activo".equals(planes.buscarUnidad(institucion, area).estado())) {
			throw CatalogoException.conflicto("idArea", "El área está inactiva. Selecciona otra área.");
		}
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

	private void validar(UUID institucion, UUID area, Turno turno, ZoneId zona) {
		Area seleccionada = exigirEspecialidadArea(institucion, area);
		if (!seleccionada.idEspecialidad().equals(turno.especialidad())) {
			throw CatalogoException.conflicto("idEspecialidad", "Esta área solo admite " + seleccionada.especialidad() + ". Selecciona un profesional de esa especialidad.");
		}
		recursos.validar(institucion, turno);
		if (!area.equals(calendario.buscarConsultorio(institucion, turno.consultorio()).idArea())) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio debe pertenecer al área seleccionada.");
		}
		if (!turno.inicio().isAfter(Instant.now())) {
			throw CatalogoException.conflicto("fecha", "Selecciona un turno que todavía no haya comenzado.");
		}
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
		carga.validarHoras(List.of(turno), vecinos, lunes, zona);
	}

	private Area exigirEspecialidadArea(UUID institucion, UUID identificador) {
		Area area = calendario.buscarArea(institucion, identificador);
		if (area.idEspecialidad() == null) {
			throw CatalogoException.conflicto("idArea", "Configura la especialidad del área antes de asignar turnos o consultorios.");
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
