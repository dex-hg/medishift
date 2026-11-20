package com.dextre.medishift.catalogos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.DatosCatalogos.AreaConsultorio;
import com.dextre.medishift.catalogos.DatosCatalogos.Consultorio;

@Repository
public class RepositorioConsultorios {

	private static final String CONSULTA = """
			SELECT r.*, area.name_planning_unit AS nombre_area, padre.id_planning_unit AS id_padre,
			       padre.name_planning_unit AS nombre_padre, etiquetas.*, etiquetas_area.*
			FROM room r
			LEFT JOIN planning_unit area ON (area.id_institution,area.id_planning_unit)=(r.id_institution,r.id_planning_unit)
			LEFT JOIN planning_unit padre ON (padre.id_institution,padre.id_planning_unit)=(area.id_institution,area.id_area_padre)
			LEFT JOIN LATERAL (
			    SELECT array_agg(s.id_specialty ORDER BY lower(s.name_specialty),s.id_specialty) AS ids_etiquetas,
			           array_agg(s.name_specialty ORDER BY lower(s.name_specialty),s.id_specialty) AS nombres_etiquetas
			    FROM room_specialty v JOIN specialty s USING (id_specialty)
			    WHERE v.id_room=r.id_room AND s.activa AND NOT r.is_general_room
			        AND s.id_institution=r.id_institution
			) etiquetas ON true
			LEFT JOIN LATERAL (
			    SELECT array_agg(s.id_specialty ORDER BY lower(s.name_specialty),s.id_specialty) AS ids_area,
			           array_agg(s.name_specialty ORDER BY lower(s.name_specialty),s.id_specialty) AS etiquetas_del_area
			    FROM area_etiqueta v JOIN specialty s ON s.id_specialty=v.id_etiqueta AND s.id_institution=v.id_institucion
			    WHERE (v.id_institucion,v.id_area)=(area.id_institution,area.id_planning_unit) AND s.activa
			) etiquetas_area ON true WHERE r.id_institution=?
			""";
	private final JdbcTemplate consultas;

	public RepositorioConsultorios(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public List<Consultorio> listar(UUID institucion) {
		return consultas.query(CONSULTA + " ORDER BY lower(r.code_room), r.id_room",
				(fila, numero) -> leer(fila), institucion);
	}

	public Optional<Consultorio> buscar(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA + " AND r.id_room = ?", (fila, numero) -> leer(fila),
				institucion, identificador).stream().findFirst();
	}

	public void bloquear(UUID institucion, UUID identificador) {
		if (consultas.query("SELECT id_room FROM room WHERE id_institution = ? AND id_room = ? FOR UPDATE",
				(fila, numero) -> fila.getObject(1, UUID.class), institucion, identificador).isEmpty()) {
			throw CatalogoException.noEncontrado();
		}
	}

	public void verificarDuplicados(UUID institucion, UUID identificador, Consultorio consultorio) {
		for (Consultorio existente : listar(institucion)) {
			if (!existente.id().equals(identificador)
					&& ValidadorCatalogos.normalizarTexto(existente.codigo()).equalsIgnoreCase(consultorio.codigo())) {
				throw CatalogoException.conflicto("codigo", "Ya existe un consultorio con ese código.");
			}
		}
	}

	public void verificarCambioEspecialidad(UUID institucion, UUID identificador, String especialidad) {
		Consultorio actual = buscar(institucion, identificador).orElseThrow(CatalogoException::noEncontrado);
		Integer cantidad = consultas.queryForObject("""
				SELECT count(*) FROM room_specialty rs JOIN room r ON r.id_room = rs.id_room
				WHERE r.id_institution = ? AND r.id_room = ?
				""", Integer.class, institucion, identificador);
		if (cantidad != null && cantidad > 1 && !ValidadorCatalogos.normalizarTexto(actual.especialidad())
				.equalsIgnoreCase(especialidad)) {
			throw CatalogoException.conflicto("especialidad",
					"Este consultorio tiene varias especialidades. Conserva su especialidad actual para editar los otros campos.");
		}
	}

	public void insertar(UUID institucion, UUID identificador, Consultorio consultorio) {
		consultas.update("""
				INSERT INTO room (id_room, id_institution, code_room, name_room, location_room,
				    is_general_room, status_room) VALUES (?, ?, ?, ?, ?, ?, ?)
				""", identificador, institucion, consultorio.codigo(), consultorio.nombre(), consultorio.ubicacion(),
				consultorio.usoGeneral(), traducirEstado(consultorio.estado()));
	}

	public void actualizar(UUID institucion, UUID identificador, Consultorio consultorio) {
		if (consultas.update("""
				UPDATE room SET code_room = ?, name_room = ?, location_room = ?, is_general_room = ?, status_room = ?
				WHERE id_institution = ? AND id_room = ?
				""", consultorio.codigo(), consultorio.nombre(), consultorio.ubicacion(),
				consultorio.usoGeneral(), traducirEstado(consultorio.estado()), institucion, identificador) == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	public void asignarEspecialidad(UUID institucion, UUID identificador, UUID especialidad) {
		List<UUID> actuales = consultas.query("""
				SELECT rs.id_specialty FROM room_specialty rs JOIN room r ON r.id_room = rs.id_room
				WHERE r.id_institution = ? AND r.id_room = ?
				""", (fila, numero) -> fila.getObject(1, UUID.class), institucion, identificador);
		if (actuales.size() > 1) {
			// verificarCambioEspecialidad ya aseguró que esta edición conserva el conjunto.
			return;
		}
		guardarEtiquetas(institucion, identificador, especialidad == null ? List.of() : List.of(especialidad));
	}

	public void guardarEtiquetas(UUID institucion, UUID identificador, List<UUID> etiquetas) {
		consultas.update("""
				DELETE FROM room_specialty rs USING room r
				WHERE rs.id_room = r.id_room AND r.id_institution = ? AND r.id_room = ?
				""", institucion, identificador);
		if (!etiquetas.isEmpty()) {
			consultas.batchUpdate("""
					INSERT INTO room_specialty (id_room, id_specialty)
					SELECT id_room, ? FROM room WHERE id_institution = ? AND id_room = ?
					""", etiquetas.stream().map(id -> new Object[] { id, institucion, identificador }).toList());
		}
	}

	public void eliminar(UUID institucion, UUID identificador) {
		Boolean tieneReferencias = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM room_block WHERE id_room = r.id_room)
				    OR EXISTS (SELECT 1 FROM shift WHERE id_institution = r.id_institution AND id_room = r.id_room)
				FROM room r WHERE r.id_institution = ? AND r.id_room = ?
				""", Boolean.class, institucion, identificador);
		if (Boolean.TRUE.equals(tieneReferencias)) {
			throw CatalogoException.conflicto("consultorio", "El consultorio tiene registros vinculados. Puedes marcarlo como Inactivo.");
		}
		consultas.update("""
				DELETE FROM room_specialty rs USING room r
				WHERE rs.id_room = r.id_room AND r.id_institution = ? AND r.id_room = ?
				""", institucion, identificador);
		if (consultas.update("DELETE FROM room WHERE id_institution = ? AND id_room = ?", institucion, identificador) == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	private Consultorio leer(ResultSet fila) throws SQLException {
		var etiquetas = RepositorioEspecialidades.leerReferencias(fila);
		UUID idArea = fila.getObject("id_planning_unit", UUID.class);
		AreaConsultorio area = idArea == null ? null : new AreaConsultorio(idArea, fila.getString("nombre_area"),
				fila.getObject("id_padre", UUID.class), fila.getString("nombre_padre"),
				RepositorioEspecialidades.leerReferencias(fila, "ids_area", "etiquetas_del_area"));
		return new Consultorio(fila.getObject("id_room", UUID.class), fila.getString("code_room"),
				fila.getString("name_room"), fila.getString("location_room"),
				etiquetas.isEmpty() ? "" : etiquetas.getFirst().nombre(),
				"active".equals(fila.getString("status_room")) ? "Activo" : "Inactivo",
				fila.getBoolean("is_general_room"), etiquetas, area);
	}

	private String traducirEstado(String estado) {
		return "Activo".equals(estado) ? "active" : "inactive";
	}

}
