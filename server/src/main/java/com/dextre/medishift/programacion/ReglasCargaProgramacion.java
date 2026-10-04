package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Requisito;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

@Component
public class ReglasCargaProgramacion {

	// Límites temporales confirmados por el usuario; aún no existe administración de políticas.
	private final int horasDiarias;
	private final int horasSemanales;

	public ReglasCargaProgramacion(@Value("${medishift.programacion.horas-diarias:6}") int horasDiarias,
			@Value("${medishift.programacion.horas-semanales:36}") int horasSemanales) {
		if (horasDiarias < 1 || horasSemanales < 1) {
			throw new IllegalArgumentException("Los límites de programación deben ser positivos.");
		}
		this.horasDiarias = horasDiarias;
		this.horasSemanales = horasSemanales;
	}

	public void validarHoras(List<Turno> propuestos, List<Turno> aprobados, LocalDate lunes, ZoneId zona) {
		Set<UUID> profesionales = new HashSet<>();
		propuestos.forEach(turno -> profesionales.add(turno.profesional()));
		List<Turno> todos = new ArrayList<>(propuestos);
		todos.addAll(aprobados);
		for (UUID profesional : profesionales) {
			Duration semanal = Duration.ZERO;
			for (int dia = 0; dia < 7; dia++) {
				LocalDate fecha = lunes.plusDays(dia);
				Instant inicioDia = fecha.atStartOfDay(zona).toInstant();
				Instant finDia = fecha.plusDays(1).atStartOfDay(zona).toInstant();
				Duration diaria = Duration.ZERO;
				for (Turno turno : todos) {
					if (turno.profesional().equals(profesional)) {
						diaria = diaria.plus(TiempoProgramacion.duracionDentro(turno.inicio(), turno.fin(), inicioDia, finDia));
					}
				}
				if (diaria.compareTo(Duration.ofHours(horasDiarias)) > 0) {
					throw CatalogoException.conflicto("horaFin", "El profesional supera el límite temporal de "
							+ horasDiarias + " horas diarias el " + fecha + ".");
				}
				semanal = semanal.plus(diaria);
			}
			if (semanal.compareTo(Duration.ofHours(horasSemanales)) > 0) {
				throw CatalogoException.conflicto("horaFin", "El profesional supera el límite temporal de "
						+ horasSemanales + " horas semanales.");
			}
		}
	}

	public void validarCobertura(List<Turno> turnos, List<Requisito> requisitos) {
		for (Requisito requisito : requisitos) {
			TreeSet<Instant> limites = new TreeSet<>();
			limites.add(requisito.inicio());
			limites.add(requisito.fin());
			for (Turno turno : turnos) {
				if (turno.especialidad().equals(requisito.especialidad())
						&& TiempoProgramacion.seSuperponen(turno.inicio(), turno.fin(), requisito.inicio(), requisito.fin())) {
					limites.add(turno.inicio().isAfter(requisito.inicio()) ? turno.inicio() : requisito.inicio());
					limites.add(turno.fin().isBefore(requisito.fin()) ? turno.fin() : requisito.fin());
				}
			}
			List<Instant> tramos = new ArrayList<>(limites);
			for (int indice = 0; indice + 1 < tramos.size(); indice++) {
				Instant inicio = tramos.get(indice);
				Instant fin = tramos.get(indice + 1);
				long cantidad = turnos.stream().filter(turno -> turno.especialidad().equals(requisito.especialidad())
						&& !turno.inicio().isAfter(inicio) && !turno.fin().isBefore(fin))
						.map(Turno::profesional).distinct().count();
				if (cantidad < requisito.cantidad()) {
					throw CatalogoException.conflicto("cobertura", "El horario no cubre la cantidad requerida de profesionales"
							+ " para una especialidad durante todo su intervalo.");
				}
			}
		}
	}

}
