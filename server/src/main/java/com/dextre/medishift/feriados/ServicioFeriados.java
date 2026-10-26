package com.dextre.medishift.feriados;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioAnual;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioFeriados;
import com.dextre.medishift.feriados.DatosFeriados.Pais;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.mensual.PeriodoMensual;

@Service
public class ServicioFeriados {

	public static final int MINUTOS_POR_FERIADO = 360;
	private final RepositorioFeriados copias;
	private final ClienteFeriados proveedor;
	private final ServicioAccesoMensual acceso;
	private final RepositorioProgramacion horarios;
	private final java.util.Map<Pais, Object> bloqueos = new java.util.EnumMap<>(Pais.class);

	public ServicioFeriados(RepositorioFeriados copias, ClienteFeriados proveedor, ServicioAccesoMensual acceso,
			RepositorioProgramacion horarios) {
		this.copias = copias; this.proveedor = proveedor; this.acceso = acceso; this.horarios = horarios;
		for (Pais pais : Pais.values()) { bloqueos.put(pais, new Object()); }
	}

	public CalendarioFeriados consultar(String zona, PeriodoMensual periodo) {
		var pais = Pais.deZona(zona);
		if (pais.isEmpty()) {
			return new CalendarioFeriados(null, "", "Pendiente", ClienteFeriados.FUENTE, null, MINUTOS_POR_FERIADO,
					false, List.of(), "No hay un país asociado a la zona horaria de esta institución. No se acreditan feriados sin verificar.");
		}
		var calendario = copias.leer(pais.get(), periodo.anio());
		if (calendario.isEmpty()) {
			return new CalendarioFeriados(pais.get().codigo(), pais.get().nombre(), "Pendiente", ClienteFeriados.FUENTE, null,
					MINUTOS_POR_FERIADO, true, List.of(), "Los feriados de este año todavía no están verificados. Actualízalos antes de consultar totales definitivos o exportar.");
		}
		var anual = calendario.get();
		boolean vencido = Duration.between(anual.actualizado(), Instant.now()).compareTo(Duration.ofHours(24)) > 0;
		return new CalendarioFeriados(pais.get().codigo(), pais.get().nombre(), "Disponible", ClienteFeriados.FUENTE,
				anual.actualizado(), MINUTOS_POR_FERIADO, vencido, anual.dias().stream()
				.filter(feriado -> periodo.contiene(feriado.fecha())).toList(),
				vencido ? "Se muestra la última copia local; conviene actualizarla." : "");
	}

	/** La consulta externa se hace fuera de las transacciones del calendario médico. */
	public CalendarioFeriados actualizar(UUID institucion, UUID actor, PeriodoMensual periodo, boolean forzar) {
		acceso.exigirAdministrador(institucion, actor);
		String zona = horarios.zona(institucion).getId();
		Pais pais = Pais.deZona(zona).orElseThrow(() -> CatalogoException.conflicto("zonaHoraria", "La zona horaria no tiene un país asociado."));
		synchronized (bloqueos.get(pais)) {
			var existente = copias.leer(pais, periodo.anio());
			if (existente.isPresent()) {
				Duration antiguedad = Duration.between(existente.get().actualizado(), Instant.now());
				if (antiguedad.compareTo(Duration.ofSeconds(5)) < 0 || (!forzar && antiguedad.compareTo(Duration.ofHours(24)) < 0)) {
					return consultar(zona, periodo);
				}
			}
			var dias = proveedor.consultar(pais, periodo.anio());
			copias.guardar(new CalendarioAnual(pais.codigo(), periodo.anio(), Instant.now(), dias), pais);
			return consultar(zona, periodo);
		}
	}
}
