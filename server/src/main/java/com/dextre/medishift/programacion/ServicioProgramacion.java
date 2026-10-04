package com.dextre.medishift.programacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Recursos;
import com.dextre.medishift.programacion.DatosProgramacion.SemanaVista;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudSemana;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudTurno;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.DatosProgramacion.TurnoVista;

@Service
public class ServicioProgramacion {

	private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
	private final RepositorioProgramacion horarios;
	private final RepositorioRecursosProgramacion recursos;
	private final RepositorioEspecialidades exclusiones;
	private final ReglasProgramacion reglas;

	public ServicioProgramacion(RepositorioProgramacion horarios, RepositorioRecursosProgramacion recursos,
			RepositorioEspecialidades exclusiones, ReglasProgramacion reglas) {
		this.horarios = horarios;
		this.recursos = recursos;
		this.exclusiones = exclusiones;
		this.reglas = reglas;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public SemanaVista listarSemana(UUID institucion, LocalDate inicio) {
		ZoneId zona = horarios.zona(institucion);
		LocalDate lunes = inicio == null ? TiempoProgramacion.inicioSemana(LocalDate.now(zona)) : inicio;
		return vistaSemana(institucion, lunes, zona);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Recursos listarRecursos(UUID institucion) { return recursos.listar(institucion); }

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public TurnoVista buscarTurno(UUID institucion, UUID identificador) {
		return vistaTurno(horarios.buscarTurno(institucion, identificador), horarios.zona(institucion));
	}

	@Transactional
	public TurnoVista crearTurno(UUID institucion, UUID actor, SolicitudTurno solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		ZoneId zona = horarios.zona(institucion);
		LocalDate lunes = TiempoProgramacion.inicioSemana(solicitud.fecha());
		Horario horario = horarios.operativo(institucion, lunes).orElse(null);
		if (horario == null || "cancelled".equals(horario.estado())) {
			horario = horarios.crearHorario(institucion, actor, lunes);
		}
		validarBorrador(horario);
		horarios.verificarSinEjecucion(institucion, horario.id());
		Turno nuevo = construir(UUID.randomUUID(), horario.id(), solicitud, zona);
		List<Turno> propuestos = activos(horarios.turnos(institucion, horario.id()));
		propuestos.add(nuevo);
		reglas.validar(institucion, horario, propuestos, false);
		horarios.insertarTurno(institucion, nuevo);
		return buscarTurno(institucion, nuevo.id());
	}

	@Transactional
	public TurnoVista actualizarTurno(UUID institucion, UUID identificador, String revision, SolicitudTurno solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		Turno actual = horarios.buscarTurno(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, RevisionesProgramacion.deTurno(actual));
		Horario horario = editable(institucion, actual);
		ZoneId zona = horarios.zona(institucion);
		Turno nuevo = construir(identificador, horario.id(), solicitud, zona);
		List<Turno> propuestos = activos(horarios.turnos(institucion, horario.id()));
		propuestos.removeIf(turno -> turno.id().equals(identificador));
		propuestos.add(nuevo);
		reglas.validar(institucion, horario, propuestos, false);
		horarios.actualizarTurno(institucion, nuevo);
		return buscarTurno(institucion, identificador);
	}

	@Transactional
	public void eliminarTurno(UUID institucion, UUID identificador, String revision) {
		exclusiones.bloquearInstitucion(institucion);
		Turno actual = horarios.buscarTurno(institucion, identificador);
		RevisionesProgramacion.comprobar(revision, RevisionesProgramacion.deTurno(actual));
		editable(institucion, actual);
		horarios.eliminarTurno(institucion, identificador);
	}

	@Transactional
	public SemanaVista aprobarSemana(UUID institucion, UUID actor, SolicitudSemana solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		Horario horario = verificarSemana(institucion, solicitud);
		validarBorrador(horario);
		horarios.verificarSinEjecucion(institucion, horario.id());
		horarios.aprobado(institucion, horario.inicio()).ifPresent(anterior ->
				horarios.verificarSinEjecucion(institucion, anterior.id()));
		List<Turno> turnos = activos(horarios.turnos(institucion, horario.id()));
		reglas.validar(institucion, horario, turnos, true);
		horarios.aprobado(institucion, horario.inicio()).ifPresent(anterior ->
				horarios.cambiarEstado(institucion, anterior.id(), "superseded"));
		horarios.aprobar(institucion, horario.id(), actor);
		return vistaSemana(institucion, horario.inicio(), horarios.zona(institucion));
	}

	@Transactional
	public SemanaVista reabrirSemana(UUID institucion, UUID actor, SolicitudSemana solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		Horario anterior = verificarSemana(institucion, solicitud);
		if (!"approved".equals(anterior.estado())) {
			throw CatalogoException.conflicto("estado", "Solo puedes reabrir la versión aprobada vigente.");
		}
		horarios.verificarSinEjecucion(institucion, anterior.id());
		Horario nuevo = horarios.crearHorario(institucion, actor, anterior.inicio());
		horarios.copiarContenido(institucion, anterior.id(), nuevo.id());
		return vistaSemana(institucion, nuevo.inicio(), horarios.zona(institucion));
	}

	@Transactional
	public SemanaVista cancelarSemana(UUID institucion, SolicitudSemana solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		Horario actual = verificarSemana(institucion, solicitud);
		if (!List.of("draft", "approved", "pending").contains(actual.estado())) {
			throw CatalogoException.conflicto("estado", "La semana ya está cancelada.");
		}
		horarios.verificarSinEjecucion(institucion, actual.id());
		horarios.aprobado(institucion, actual.inicio()).ifPresent(aprobado ->
				horarios.verificarSinEjecucion(institucion, aprobado.id()));
		horarios.aprobado(institucion, actual.inicio()).ifPresent(aprobado ->
				horarios.cambiarEstado(institucion, aprobado.id(), "cancelled"));
		if (!"approved".equals(actual.estado())) { horarios.cambiarEstado(institucion, actual.id(), "cancelled"); }
		return vistaSemana(institucion, actual.inicio(), horarios.zona(institucion));
	}

	private Horario verificarSemana(UUID institucion, SolicitudSemana solicitud) {
		horarios.buscarHorario(institucion, solicitud.idHorario());
		Horario actual = horarios.operativo(institucion, solicitud.fechaInicio()).orElseThrow(CatalogoException::noEncontrado);
		if (!actual.id().equals(solicitud.idHorario())) {
			throw CatalogoException.conflicto("revision", "Existe otra versión operativa. Recarga la semana.");
		}
		SemanaVista vista = vistaSemana(institucion, solicitud.fechaInicio(), horarios.zona(institucion));
		RevisionesProgramacion.comprobar(solicitud.revision(), vista.revision());
		return actual;
	}

	private Horario editable(UUID institucion, Turno turno) {
		Horario horario = horarios.buscarHorario(institucion, turno.horario());
		validarBorrador(horario);
		horarios.verificarSinEjecucion(institucion, horario.id());
		if (!"draft".equals(turno.estado()) || !horarios.operativo(institucion, horario.inicio())
				.map(vigente -> vigente.id().equals(horario.id())).orElse(false)) {
			throw CatalogoException.conflicto("estado", "Solo puedes editar turnos del borrador operativo actual.");
		}
		return horario;
	}

	private void validarBorrador(Horario horario) {
		if (!"draft".equals(horario.estado())) {
			throw CatalogoException.conflicto("estado", "El horario no es editable. Reabre la versión aprobada antes de modificar turnos.");
		}
	}

	private Turno construir(UUID identificador, UUID horario, SolicitudTurno solicitud, ZoneId zona) {
		return new Turno(identificador, horario, solicitud.idProfesional(), solicitud.idConsultorio(),
				solicitud.idEspecialidad(), TiempoProgramacion.resolver(solicitud.fecha(), solicitud.horaInicio(), zona),
				TiempoProgramacion.resolver(solicitud.fecha(), solicitud.horaFin(), zona), "draft", solicitud.observacion(), "", "", "");
	}

	private List<Turno> activos(List<Turno> turnos) {
		return new ArrayList<>(turnos.stream().filter(turno -> !List.of("cancelled", "superseded").contains(turno.estado())).toList());
	}

	private SemanaVista vistaSemana(UUID institucion, LocalDate lunes, ZoneId zona) {
		Horario horario = horarios.operativo(institucion, lunes).orElse(null);
		if (horario == null) {
			return new SemanaVista(lunes, lunes.plusDays(6), zona.getId(), null, 0, "Sin horario",
					RevisionesProgramacion.resumir(institucion, lunes, zona.getId()), List.of(), null);
		}
		List<Turno> turnos = horarios.turnos(institucion, horario.id());
		Integer aprobada = horarios.aprobado(institucion, lunes).map(Horario::version).orElse(null);
		String revision = RevisionesProgramacion.resumir(zona.getId(),
				RevisionesProgramacion.deSemana(horario, turnos, horarios.requisitos(horario.id())), aprobada);
		return new SemanaVista(lunes, lunes.plusDays(6), zona.getId(), horario.id(), horario.version(),
				traducirEstado(horario.estado()), revision, turnos.stream().map(turno -> vistaTurno(turno, zona)).toList(), aprobada);
	}

	private TurnoVista vistaTurno(Turno turno, ZoneId zona) {
		return new TurnoVista(turno.id(), turno.profesional(), turno.nombreProfesional(), turno.consultorio(),
				turno.nombreConsultorio(), turno.especialidad(), turno.nombreEspecialidad(),
				turno.inicio().atZone(zona).toLocalDate(), FORMATO_HORA.format(turno.inicio().atZone(zona)),
				FORMATO_HORA.format(turno.fin().atZone(zona)), traducirEstado(turno.estado()), turno.observacion(),
				RevisionesProgramacion.deTurno(turno), zona.getId());
	}

	private String traducirEstado(String estado) {
		return switch (estado) {
			case "draft" -> "Borrador";
			case "approved" -> "Aprobado";
			case "cancelled" -> "Cancelado";
			case "superseded" -> "Sustituido";
			case "pending" -> "Pendiente";
			default -> throw new IllegalStateException("Estado de programación no admitido.");
		};
	}

}
