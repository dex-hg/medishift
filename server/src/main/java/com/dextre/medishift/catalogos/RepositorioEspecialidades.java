package com.dextre.medishift.catalogos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.DatosCatalogos.Especialidad;

@Repository
public class RepositorioEspecialidades {

	private final JdbcTemplate consultas;

	public RepositorioEspecialidades(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public List<Especialidad> listar(UUID institucion) {
		return consultas.query("""
				SELECT id_specialty, name_specialty FROM specialty WHERE id_institution IS NULL OR id_institution = ?
				ORDER BY lower(name_specialty), id_specialty
				""",
				(fila, numero) -> new Especialidad(fila.getObject("id_specialty", UUID.class),
						fila.getString("name_specialty")), institucion);
	}

	public Especialidad buscar(UUID institucion, UUID identificador) {
		return listar(institucion).stream().filter(especialidad -> especialidad.id().equals(identificador)).findFirst()
				.orElseThrow(() -> CatalogoException.conflicto("idEspecialidad", "Selecciona una especialidad de tu catálogo."));
	}

	public Especialidad crear(UUID institucion, String nombre) {
		bloquearInstitucion(institucion);
		if (listar(institucion).stream().anyMatch(especialidad -> ValidadorCatalogos.normalizarTexto(especialidad.nombre()).equalsIgnoreCase(nombre))) {
			throw CatalogoException.conflicto("nombre", "Ya existe una especialidad con ese nombre. Selecciónala en la lista.");
		}
		return buscar(institucion, resolver(institucion, nombre));
	}

	/** Serializa las reglas de duplicados que el esquema no distingue por mayúsculas. */
	public void bloquearInstitucion(UUID institucion) {
		bloquear("catalogos:" + institucion);
	}

	public UUID resolver(UUID institucion, String nombre) {
		String normalizado = ValidadorCatalogos.normalizarTexto(nombre).toLowerCase(Locale.ROOT);
		String codigo = crearCodigo(institucion + ":" + normalizado);
		bloquear("especialidad:" + codigo);
		List<Especialidad> existentes = listar(institucion);
		for (Especialidad existente : existentes) {
			if (ValidadorCatalogos.normalizarTexto(existente.nombre()).toLowerCase(Locale.ROOT).equals(normalizado)) {
				return existente.id();
			}
		}
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO specialty (id_specialty, code_specialty, name_specialty, id_institution)
				VALUES (?, ?, ?, ?) ON CONFLICT (code_specialty) DO NOTHING
				""", identificador, codigo, nombre, institucion);
		Especialidad especialidad = consultas.query("""
				SELECT id_specialty, name_specialty FROM specialty WHERE code_specialty = ?
				""", (fila, numero) -> new Especialidad(fila.getObject("id_specialty", UUID.class),
				fila.getString("name_specialty")), codigo).stream().findFirst().orElseThrow();
		if (!ValidadorCatalogos.normalizarTexto(especialidad.nombre()).toLowerCase(Locale.ROOT).equals(normalizado)) {
			throw CatalogoException.conflicto("especialidad", "No se pudo asignar ese nombre de especialidad.");
		}
		return especialidad.id();
	}

	private void bloquear(String clave) {
		consultas.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", fila -> {
			// El bloqueo dura hasta completar o revertir la transacción del servicio.
		}, clave);
	}

	private String crearCodigo(String nombre) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(nombre.getBytes(StandardCharsets.UTF_8));
			return "ESP-" + HexFormat.of().formatHex(resumen).substring(0, 28);
		} catch (NoSuchAlgorithmException excepcion) {
			throw new IllegalStateException("El entorno no admite SHA-256.", excepcion);
		}
	}

}
