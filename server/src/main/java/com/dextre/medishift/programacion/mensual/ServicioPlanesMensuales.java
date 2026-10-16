package com.dextre.medishift.programacion.mensual;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.ListadoPlanes;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.ListadoUnidades;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.PlanMensual;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudPlan;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.SolicitudUnidad;
import com.dextre.medishift.programacion.mensual.DatosPlanesMensuales.UnidadPlanificacion;

@Service
public class ServicioPlanesMensuales {

	private final ServicioAccesoMensual acceso;
	private final RepositorioPlanesMensuales planes;
	private final RepositorioProgramacion horarios;
	private final RepositorioEspecialidades exclusiones;

	public ServicioPlanesMensuales(ServicioAccesoMensual acceso, RepositorioPlanesMensuales planes,
			RepositorioProgramacion horarios, RepositorioEspecialidades exclusiones) {
		this.acceso = acceso;
		this.planes = planes;
		this.horarios = horarios;
		this.exclusiones = exclusiones;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public ListadoUnidades listarUnidades(UUID institucion, UUID actor) {
		prepararLectura(institucion, actor);
		return new ListadoUnidades(horarios.zona(institucion).getId(), planes.unidades(institucion));
	}

	@Transactional
	public UnidadPlanificacion crearUnidad(UUID institucion, UUID actor, SolicitudUnidad solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		prepararLectura(institucion, actor);
		UnidadPlanificacion unidad = planes.crearUnidad(institucion, solicitud);
		planes.registrarCreacion(institucion, actor, unidad.id(), true);
		return unidad;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public ListadoPlanes listarPlanes(UUID institucion, UUID actor, UUID unidad, PeriodoMensual periodo) {
		prepararLectura(institucion, actor);
		planes.buscarUnidad(institucion, unidad);
		return new ListadoPlanes(planes.planes(institucion, unidad, periodo));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PlanMensual buscarPlan(UUID institucion, UUID actor, UUID identificador) {
		prepararLectura(institucion, actor);
		return planes.buscarPlan(institucion, identificador);
	}

	@Transactional
	public PlanMensual crearPlan(UUID institucion, UUID actor, SolicitudPlan solicitud) {
		exclusiones.bloquearInstitucion(institucion);
		prepararLectura(institucion, actor);
		UnidadPlanificacion unidad = planes.buscarUnidad(institucion, solicitud.idUnidad());
		if (!"Activo".equals(unidad.estado())) {
			throw CatalogoException.conflicto("idUnidad", "La unidad está inactiva. Puedes consultar su historial, pero no crear planes.");
		}
		horarios.zona(institucion);
		PlanMensual plan = planes.crearBorrador(institucion, actor, unidad.id(), solicitud.periodo());
		planes.registrarCreacion(institucion, actor, plan.id(), false);
		return plan;
	}

	private void prepararLectura(UUID institucion, UUID actor) {
		acceso.exigirAdministrador(institucion, actor);
		planes.verificarEsquema();
	}
}
