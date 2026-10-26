package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.AsistenciaTurno;

@Service
public class ServicioFaltas {

	private final ServicioAccesoMensual acceso;
	private final RepositorioEspecialidades exclusiones;
	private final RepositorioPlanificador calendario;
	private final RepositorioFaltas faltas;

	public ServicioFaltas(ServicioAccesoMensual acceso, RepositorioEspecialidades exclusiones,
			RepositorioPlanificador calendario, RepositorioFaltas faltas) {
		this.acceso = acceso;
		this.exclusiones = exclusiones;
		this.calendario = calendario;
		this.faltas = faltas;
	}

	@Transactional
	public AsistenciaTurno cambiar(UUID institucion, UUID actor, UUID identificador, String revision, boolean falta) {
		exclusiones.bloquearInstitucion(institucion);
		acceso.exigirAdministrador(institucion, actor);
		calendario.verificarEsquema();
		var actual = calendario.buscarTurno(institucion, identificador);
		if (!List.of("draft", "pending", "approved").contains(actual.estadoPlan())
				|| !List.of("draft", "pending", "approved").contains(actual.turno().estado())) {
			throw CatalogoException.conflicto("estado", "Solo puedes registrar faltas en turnos vigentes del calendario mensual.");
		}
		if (!actual.revisionAsistencia().equals(revision)) {
			throw CatalogoException.conflicto("revision", "El turno o su asistencia cambió. Actualiza los datos antes de continuar.");
		}
		String estado = falta ? "Falta" : "Asistencia";
		if (estado.equals(actual.asistencia())) {
			return new AsistenciaTurno(identificador, actual.asistencia(), actual.revisionAsistencia());
		}
		UUID registro = actual.idAsistencia() == null ? UUID.randomUUID() : actual.idAsistencia();
		if (falta) {
			faltas.marcarFalta(institucion, actor, identificador, registro);
		} else {
			faltas.restablecerAsistencia(institucion, actor, identificador);
		}
		var actualizado = calendario.buscarTurno(institucion, identificador);
		faltas.auditar(institucion, actor, registro, actual.asistenciaRegistrada(), actualizado.asistenciaRegistrada());
		return new AsistenciaTurno(identificador, actualizado.asistencia(), actualizado.revisionAsistencia());
	}
}
