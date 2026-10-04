package com.dextre.medishift.programacion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.TiempoProgramacion.Ventana;

@Component
public class ReglasProgramacion {

	private final JdbcTemplate consultas;
	private final RepositorioProgramacion horarios;
	private final RepositorioRecursosProgramacion recursos;
	private final ReglasCargaProgramacion carga;

	public ReglasProgramacion(JdbcTemplate consultas, RepositorioProgramacion horarios,
			RepositorioRecursosProgramacion recursos, ReglasCargaProgramacion carga) {
		this.consultas = consultas;
		this.horarios = horarios;
		this.recursos = recursos;
		this.carga = carga;
	}

	public void validar(UUID institucion, Horario horario, List<Turno> turnos, boolean aprobar) {
		ZoneId zona = horarios.zona(institucion);
		for (Turno turno : turnos) {
			recursos.validar(institucion, turno);
			if (!turno.inicio().isAfter(Instant.now())) {
				throw CatalogoException.conflicto("fecha", "Solo puedes programar y aprobar turnos que aún no han iniciado.");
			}
			LocalDate fecha = turno.inicio().atZone(zona).toLocalDate();
			if (fecha.isBefore(horario.inicio()) || fecha.isAfter(horario.fin())) {
				throw CatalogoException.conflicto("fecha", "El turno debe quedar dentro de la semana del horario.");
			}
			validarDisponibilidad(institucion, turno, zona);
			validarBloqueos(turno);
		}
		Instant inicio = horario.inicio().atStartOfDay(zona).toInstant();
		Instant fin = horario.fin().plusDays(1).atStartOfDay(zona).toInstant();
		UUID reemplazo = horarios.aprobado(institucion, horario.inicio()).map(Horario::id).orElse(null);
		List<Turno> aprobados = horarios.aprobadosEn(institucion, inicio, fin, reemplazo);
		validarSolapamientos(turnos, aprobados);
		carga.validarHoras(turnos, aprobados, horario.inicio(), zona);
		if (aprobar) {
			if (turnos.isEmpty()) { throw CatalogoException.conflicto("turnos", "Añade al menos un turno antes de aprobar."); }
			carga.validarCobertura(turnos, horarios.requisitos(horario.id()));
		}
	}

	/** Se llama después de modificar disponibilidades, dentro de la misma transacción y bloqueo institucional. */
	public void validarCambioDisponibilidades(UUID institucion, UUID profesional) {
		ZoneId zona = horarios.zona(institucion);
		for (Turno turno : horarios.futuros(institucion, profesional)) {
			validarDisponibilidad(institucion, turno, zona);
		}
	}

	/** Protege turnos futuros después de actualizar catálogos; una excepción revierte toda la edición. */
	public void validarCatalogosFuturos(UUID institucion) {
		for (Turno turno : horarios.futuros(institucion, null)) { recursos.validar(institucion, turno); }
	}

	public List<Ventana> disponibilidades(UUID institucion, UUID profesional) {
		return consultas.query("""
				SELECT a.* FROM availability a JOIN professional p ON p.id_professional = a.id_professional
				WHERE p.id_institution = ? AND p.id_professional = ? AND a.status_availability = 'active'
				""", (fila, numero) -> new Ventana(fila.getInt("weekday_availability"),
				fila.getTime("start_time_availability").toLocalTime(), fila.getTime("end_time_availability").toLocalTime(),
				fila.getDate("valid_from_availability").toLocalDate(), fila.getDate("valid_to_availability").toLocalDate()),
				institucion, profesional);
	}

	private void validarDisponibilidad(UUID institucion, Turno turno, ZoneId zona) {
		LocalDate fecha = turno.inicio().atZone(zona).toLocalDate();
		LocalTime inicio = turno.inicio().atZone(zona).toLocalTime();
		LocalTime fin = turno.fin().atZone(zona).toLocalTime();
		if (!fecha.equals(turno.fin().atZone(zona).toLocalDate()) || !fin.isAfter(inicio)) {
			throw CatalogoException.conflicto("horaFin", "Los turnos nocturnos aún no están habilitados.");
		}
		if (!TiempoProgramacion.resolver(fecha, inicio, zona).equals(turno.inicio())
				|| !TiempoProgramacion.resolver(fecha, fin, zona).equals(turno.fin())) {
			throw CatalogoException.conflicto("hora", "La zona horaria ya no coincide con los instantes del turno.");
		}
		if (!TiempoProgramacion.cubrirDisponibilidad(disponibilidades(institucion, turno.profesional()), fecha, inicio, fin)) {
			throw CatalogoException.conflicto("disponibilidad", "El turno del " + fecha
					+ " no está cubierto por la disponibilidad activa del profesional. Conserva una cobertura completa.");
		}
	}

	private void validarBloqueos(Turno turno) {
		Boolean ausencia = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM professional_leave WHERE id_professional = ?
				AND status_professional_leave = 'approved' AND start_at_professional_leave < ? AND end_at_professional_leave > ?)
				""", Boolean.class, turno.profesional(), Timestamp.from(turno.fin()), Timestamp.from(turno.inicio()));
		if (Boolean.TRUE.equals(ausencia)) {
			throw CatalogoException.conflicto("idProfesional", "El profesional tiene un permiso aprobado durante el turno.");
		}
		Boolean bloqueo = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM room_block WHERE id_room = ? AND status_room_block = 'active'
				AND start_at_room_block < ? AND end_at_room_block > ?)
				""", Boolean.class, turno.consultorio(), Timestamp.from(turno.fin()), Timestamp.from(turno.inicio()));
		if (Boolean.TRUE.equals(bloqueo)) {
			throw CatalogoException.conflicto("idConsultorio", "El consultorio tiene un bloqueo activo durante el turno.");
		}
	}

	private void validarSolapamientos(List<Turno> propuestos, List<Turno> aprobados) {
		List<Turno> vistos = new ArrayList<>(aprobados);
		for (Turno turno : propuestos) {
			for (Turno existente : vistos) {
				if (TiempoProgramacion.seSuperponen(turno.inicio(), turno.fin(), existente.inicio(), existente.fin())) {
					if (turno.profesional().equals(existente.profesional())) {
						throw CatalogoException.conflicto("idProfesional", "El profesional ya tiene un turno que se superpone.");
					}
					if (turno.consultorio().equals(existente.consultorio())) {
						throw CatalogoException.conflicto("idConsultorio", "El consultorio ya tiene un turno que se superpone.");
					}
				}
			}
			vistos.add(turno);
		}
	}

}
