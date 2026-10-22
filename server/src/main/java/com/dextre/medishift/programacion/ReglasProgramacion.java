package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Component
public class ReglasProgramacion {

	private final RepositorioRestriccionesProgramacion restricciones;
	private final RepositorioProgramacion horarios;
	private final RepositorioRecursosProgramacion recursos;
	private final ReglasCargaProgramacion carga;

	public ReglasProgramacion(RepositorioRestriccionesProgramacion restricciones, RepositorioProgramacion horarios,
			RepositorioRecursosProgramacion recursos, ReglasCargaProgramacion carga) {
		this.restricciones = restricciones;
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
			validarIntervalo(turno, zona);
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

	/** Protege turnos futuros después de actualizar catálogos; una excepción revierte toda la edición. */
	public void validarCatalogosFuturos(UUID institucion) {
		for (Turno turno : horarios.futuros(institucion, null)) { recursos.validar(institucion, turno); }
	}

	private void validarIntervalo(Turno turno, ZoneId zona) {
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
	}

	private void validarBloqueos(Turno turno) {
		if (restricciones.tieneAusenciaAprobada(turno.profesional(), turno.inicio(), turno.fin())) {
			throw CatalogoException.conflicto("idProfesional", "El profesional tiene un permiso aprobado durante el turno.");
		}
		if (restricciones.tieneBloqueoActivo(turno.consultorio(), turno.inicio(), turno.fin())) {
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
