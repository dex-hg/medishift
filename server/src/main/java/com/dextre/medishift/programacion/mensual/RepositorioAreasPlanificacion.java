package com.dextre.medishift.programacion.mensual;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.DatosProgramacion.EspecialidadRecurso;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.Area;
import com.dextre.medishift.programacion.mensual.DatosPlanificador.SolicitudArea;

@Repository
public class RepositorioAreasPlanificacion {
	private static final String CONSULTA = """
			SELECT u.*, etiquetas.* FROM planning_unit u
			LEFT JOIN LATERAL (
			    SELECT array_agg(e.id_specialty ORDER BY lower(e.name_specialty),e.id_specialty) AS ids_etiquetas,
			           array_agg(e.name_specialty ORDER BY lower(e.name_specialty),e.id_specialty) AS nombres_etiquetas
			    FROM area_etiqueta a JOIN specialty e ON e.id_specialty=a.id_etiqueta AND e.id_institution=a.id_institucion AND e.activa
			    WHERE (a.id_institucion,a.id_area)=(u.id_institution,u.id_planning_unit)
			) etiquetas ON true WHERE u.id_institution=?
			""";
	private final JdbcTemplate consultas;
	private final RepositorioEspecialidades auditoria;

	public RepositorioAreasPlanificacion(JdbcTemplate consultas, RepositorioEspecialidades auditoria) {
		this.consultas = consultas;
		this.auditoria = auditoria;
	}

	public List<Area> listar(UUID institucion) {
		return consultas.query(CONSULTA + " ORDER BY lower(u.name_planning_unit),u.id_planning_unit",
				(fila, numero) -> leer(fila), institucion);
	}

	public Area buscar(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA + " AND u.id_planning_unit=?", (fila, numero) -> leer(fila), institucion, identificador)
				.stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public void guardar(UUID institucion, UUID area, SolicitudArea datos) {
		if (datos.idAreaPadre() != null) {
			Area padre = buscar(institucion, datos.idAreaPadre());
			if (padre.id().equals(area) || padre.idAreaPadre() != null
					|| ("Activo".equals(datos.estado()) && !"Activo".equals(padre.estado()))) {
				throw CatalogoException.conflicto("idAreaPadre", "Selecciona un área raíz válida para esta subárea.");
			}
		}
		if (datos.idAreaPadre() != null || "Inactivo".equals(datos.estado())) {
			boolean soloActivas = datos.idAreaPadre() == null;
			Boolean tieneSubareas = consultas.queryForObject("""
					SELECT EXISTS (SELECT 1 FROM planning_unit WHERE id_institution=? AND id_area_padre=?
					    AND (? IS FALSE OR status_planning_unit='active'))
					""", Boolean.class, institucion, area, soloActivas);
			if (Boolean.TRUE.equals(tieneSubareas)) {
				throw CatalogoException.conflicto(soloActivas ? "estado" : "idAreaPadre",
						"Resuelve las subáreas vinculadas antes de cambiar esta unidad.");
			}
		}
		consultas.update("DELETE FROM area_etiqueta WHERE id_institucion=? AND id_area=?", institucion, area);
		consultas.batchUpdate("INSERT INTO area_etiqueta(id_institucion,id_area,id_etiqueta) VALUES (?,?,?)",
				datos.idsEtiquetas().stream().map(id -> new Object[] { institucion, area, id }).toList());
		UUID singular = datos.idsEtiquetas().size() == 1 ? datos.idsEtiquetas().getFirst() : null;
		if (consultas.update("""
				UPDATE planning_unit SET name_planning_unit=?,id_area_padre=?,status_planning_unit=?,id_specialty=?
				WHERE id_institution=? AND id_planning_unit=?
				""", datos.nombre(), datos.idAreaPadre(), "Activo".equals(datos.estado()) ? "active" : "inactive",
				singular, institucion, area) != 1) throw CatalogoException.noEncontrado();
	}

	public String captura(UUID institucion, UUID area) {
		return consultas.queryForObject("""
				SELECT (to_jsonb(u)||jsonb_build_object('idsEtiquetas',ARRAY(
				    SELECT id_etiqueta FROM area_etiqueta a
				    WHERE (a.id_institucion,a.id_area)=(u.id_institution,u.id_planning_unit) ORDER BY id_etiqueta)))::text
				FROM planning_unit u WHERE id_institution=? AND id_planning_unit=?
				""", String.class, institucion, area);
	}

	public void auditar(UUID institucion, UUID actor, UUID area, String antes) {
		auditoria.auditar(institucion, actor, "planning_unit", area, antes == null ? "create" : "update",
				antes, captura(institucion, area));
	}

	private Area leer(ResultSet fila) throws SQLException {
		return new Area(fila.getObject("id_planning_unit", UUID.class), fila.getString("code_planning_unit"),
				fila.getString("name_planning_unit"), "active".equals(fila.getString("status_planning_unit")) ? "Activo" : "Inactivo",
				fila.getObject("id_area_padre", UUID.class), RepositorioEspecialidades.leerReferencias(fila).stream()
						.map(etiqueta -> new EspecialidadRecurso(etiqueta.id(), etiqueta.nombre())).toList());
	}
}
