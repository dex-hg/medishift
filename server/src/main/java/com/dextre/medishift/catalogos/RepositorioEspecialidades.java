package com.dextre.medishift.catalogos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.DatosCatalogos.CambioEtiqueta;
import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;
import com.dextre.medishift.catalogos.DatosCatalogos.Etiqueta;

@Repository
public class RepositorioEspecialidades {

	private static final String CONSULTA = """
			SELECT id_specialty,name_specialty,activa,id_institution IS NULL AS compartida
			FROM specialty WHERE (id_institution IS NULL OR id_institution=?)
			""";
	private final JdbcTemplate consultas;

	public RepositorioEspecialidades(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public List<Etiqueta> listarEtiquetas(UUID institucion) {
		return consultas.query(CONSULTA + "ORDER BY activa DESC, lower(name_specialty), compartida, id_specialty",
				(fila, numero) -> leer(fila), institucion);
	}

	public List<Especialidad> listar(UUID institucion) {
		return listarEtiquetas(institucion).stream().filter(Etiqueta::activa)
				.map(etiqueta -> new Especialidad(etiqueta.id(), etiqueta.nombre())).toList();
	}

	public Especialidad buscar(UUID institucion, UUID identificador) {
		return listar(institucion).stream().filter(etiqueta -> etiqueta.id().equals(identificador)).findFirst()
				.orElseThrow(() -> CatalogoException.conflicto("idEspecialidad", "Selecciona una etiqueta activa de tu catálogo."));
	}

	public Etiqueta buscarEtiqueta(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA + "AND id_specialty=?", (fila, numero) -> leer(fila), institucion, identificador)
				.stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
	}

	public void exigirActivas(UUID institucion, List<UUID> identificadores) {
		for (UUID identificador : identificadores) {
			Etiqueta etiqueta = buscarEtiqueta(institucion, identificador);
			if (!etiqueta.activa()) {
				throw CatalogoException.conflicto("idsEtiquetas", "La etiqueta «" + etiqueta.nombre() + "» está archivada.");
			}
		}
	}

	public Especialidad crear(UUID institucion, String nombre) {
		bloquearInstitucion(institucion);
		Etiqueta etiqueta = crearEtiqueta(institucion, nombre);
		return new Especialidad(etiqueta.id(), etiqueta.nombre());
	}

	public Etiqueta crearEtiqueta(UUID institucion, String nombre) {
		exigirNombreDisponible(institucion, null, nombre);
		return insertar(institucion, nombre);
	}

	public Etiqueta cambiar(UUID institucion, UUID identificador, CambioEtiqueta cambio) {
		exigirNombreDisponible(institucion, identificador, cambio.nombre());
		if (consultas.update("UPDATE specialty SET name_specialty=?,activa=? WHERE id_institution=? AND id_specialty=?",
				cambio.nombre(), cambio.activa(), institucion, identificador) != 1) throw CatalogoException.noEncontrado();
		return buscarEtiqueta(institucion, identificador);
	}

	/** Serializa la programación, la edición de competencias y la comprobación de turnos vigentes. */
	public void bloquearInstitucion(UUID institucion) {
		consultas.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", fila -> { }, "catalogos:" + institucion);
	}

	public UUID resolver(UUID institucion, String nombre) {
		return listarEtiquetas(institucion).stream()
				.filter(etiqueta -> normalizado(etiqueta.nombre()).equals(normalizado(nombre)))
				.findFirst().map(etiqueta -> {
					if (!etiqueta.activa()) throw CatalogoException.conflicto("especialidad", "Reactiva esta etiqueta en el catálogo.");
					return etiqueta.id();
				}).orElseGet(() -> insertar(institucion, nombre).id());
	}

	public void auditar(UUID institucion, UUID actor, String entidad, UUID identificador,
			String accion, String antes, String despues) {
		consultas.update("""
				INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,
					entity_id_audit_event,action_audit_event,before_audit_event,after_audit_event)
				VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb)
				""", UUID.randomUUID(), institucion, actor, entidad, identificador, accion, antes, despues);
	}

	/** Los arreglos salen del mismo orden en la consulta de catálogo y recursos. */
	public static List<Especialidad> leerReferencias(ResultSet fila) throws SQLException {
		var ids = fila.getArray("ids_etiquetas");
		if (ids == null) return List.of();
		Object[] identificadores = (Object[]) ids.getArray();
		String[] nombres = (String[]) fila.getArray("nombres_etiquetas").getArray();
		List<Especialidad> etiquetas = new ArrayList<>(identificadores.length);
		for (int indice = 0; indice < identificadores.length; indice++) {
			etiquetas.add(new Especialidad(UUID.fromString(identificadores[indice].toString()), nombres[indice]));
		}
		return List.copyOf(etiquetas);
	}

	private Etiqueta insertar(UUID institucion, String nombre) {
		UUID identificador = UUID.randomUUID();
		String codigo = "ET-" + identificador.toString().replace("-", "").substring(0, 29);
		consultas.update("""
				INSERT INTO specialty(id_specialty,code_specialty,name_specialty,id_institution) VALUES (?,?,?,?)
				""", identificador, codigo, nombre, institucion);
		return new Etiqueta(identificador, nombre, true, false);
	}

	private void exigirNombreDisponible(UUID institucion, UUID excluir, String nombre) {
		String clave = normalizado(nombre);
		if (listarEtiquetas(institucion).stream().anyMatch(etiqueta ->
				!etiqueta.id().equals(excluir) && normalizado(etiqueta.nombre()).equals(clave))) {
			throw CatalogoException.conflicto("nombre", "Ya existe una etiqueta con ese nombre en el catálogo.");
		}
	}

	private static Etiqueta leer(ResultSet fila) throws SQLException {
		return new Etiqueta(fila.getObject("id_specialty", UUID.class), fila.getString("name_specialty"),
				fila.getBoolean("activa"), fila.getBoolean("compartida"));
	}

	private static String normalizado(String nombre) {
		return ValidadorCatalogos.normalizarTexto(nombre).toLowerCase(Locale.ROOT);
	}
}
