package com.dextre.medishift.catalogos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;

@Repository
public class RepositorioProfesionales {

	private static final String CONSULTA = """
			SELECT p.*, COALESCE((SELECT s.name_specialty FROM professional_specialty ps
			    JOIN specialty s ON s.id_specialty = ps.id_specialty
			    WHERE ps.id_professional = p.id_professional
			    ORDER BY ps.is_primary_professional_specialty DESC, lower(s.name_specialty), s.id_specialty
			    LIMIT 1), '') AS especialidad
			FROM professional p WHERE p.id_institution = ?
			""";
	private final JdbcTemplate consultas;

	public RepositorioProfesionales(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public List<Profesional> listar(UUID institucion) {
		return consultas.query(CONSULTA + " ORDER BY lower(p.last_name_professional), lower(p.first_name_professional), p.id_professional",
				(fila, numero) -> leer(fila), institucion);
	}

	public Optional<Profesional> buscar(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA + " AND p.id_professional = ?",
				(fila, numero) -> leer(fila), institucion, identificador).stream().findFirst();
	}

	public void bloquear(UUID institucion, UUID identificador) {
		if (consultas.query("""
				SELECT id_professional FROM professional
				WHERE id_institution = ? AND id_professional = ? FOR UPDATE
				""", (fila, numero) -> fila.getObject(1, UUID.class), institucion, identificador).isEmpty()) {
			throw CatalogoException.noEncontrado();
		}
	}

	public void verificarDuplicados(UUID institucion, UUID identificador, Profesional profesional) {
		List<Profesional> existentes = listar(institucion);
		for (Profesional existente : existentes) {
			if (existente.id().equals(identificador)) {
				continue;
			}
			if (ValidadorCatalogos.normalizarTexto(existente.colegiatura()).equalsIgnoreCase(profesional.colegiatura())) {
				throw CatalogoException.conflicto("colegiatura", "Ya existe un profesional con esa colegiatura.");
			}
			if (existente.correo().trim().equalsIgnoreCase(profesional.correo())) {
				throw CatalogoException.conflicto("correo", "Ya existe un profesional con ese correo.");
			}
		}
	}

	public void insertar(UUID institucion, UUID identificador, Profesional profesional) {
		consultas.update("""
				INSERT INTO professional (id_professional, id_institution, first_name_professional,
				    last_name_professional, license_number_professional, category_professional,
				    email_professional, phone_professional, status_professional)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", identificador, institucion, profesional.nombres(), profesional.apellidos(),
				profesional.colegiatura(), profesional.categoria(), profesional.correo(),
				profesional.telefono().isEmpty() ? null : profesional.telefono(), traducirEstado(profesional.estado()));
	}

	public void actualizar(UUID institucion, UUID identificador, Profesional profesional) {
		int modificados = consultas.update("""
				UPDATE professional SET first_name_professional = ?, last_name_professional = ?,
				    license_number_professional = ?, category_professional = ?, email_professional = ?,
				    phone_professional = ?, status_professional = ?
				WHERE id_institution = ? AND id_professional = ?
				""", profesional.nombres(), profesional.apellidos(), profesional.colegiatura(),
				profesional.categoria(), profesional.correo(), profesional.telefono().isEmpty() ? null : profesional.telefono(),
				traducirEstado(profesional.estado()), institucion, identificador);
		if (modificados == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	/** La antigua primaria queda secundaria para conservar competencias y turnos históricos. */
	public void asignarEspecialidad(UUID institucion, UUID identificador, UUID especialidad) {
		consultas.update("""
				UPDATE professional_specialty ps SET is_primary_professional_specialty = false
				FROM professional p WHERE ps.id_professional = p.id_professional
				    AND p.id_institution = ? AND p.id_professional = ?
				    AND ps.is_primary_professional_specialty
				""", institucion, identificador);
		consultas.update("""
				INSERT INTO professional_specialty (id_professional, id_specialty, is_primary_professional_specialty)
				SELECT id_professional, ?, true FROM professional WHERE id_institution = ? AND id_professional = ?
				ON CONFLICT (id_professional, id_specialty) DO UPDATE SET is_primary_professional_specialty = true
				""", especialidad, institucion, identificador);
	}

	public void eliminar(UUID institucion, UUID identificador) {
		Boolean tieneReferencias = consultas.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM availability WHERE id_professional = p.id_professional)
				    OR EXISTS (SELECT 1 FROM professional_leave WHERE id_professional = p.id_professional)
				    OR EXISTS (SELECT 1 FROM shift WHERE id_institution = p.id_institution
				        AND id_professional = p.id_professional)
				FROM professional p WHERE id_institution = ? AND id_professional = ?
				""", Boolean.class, institucion, identificador);
		if (Boolean.TRUE.equals(tieneReferencias)) {
			throw CatalogoException.conflicto("profesional", "El profesional tiene registros vinculados. Puedes marcarlo como Inactivo.");
		}
		consultas.update("""
				DELETE FROM professional_specialty ps USING professional p
				WHERE ps.id_professional = p.id_professional AND p.id_institution = ? AND p.id_professional = ?
				""", institucion, identificador);
		if (consultas.update("DELETE FROM professional WHERE id_institution = ? AND id_professional = ?",
				institucion, identificador) == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	private Profesional leer(ResultSet fila) throws SQLException {
		String telefono = fila.getString("phone_professional");
		return new Profesional(fila.getObject("id_professional", UUID.class),
				fila.getString("first_name_professional"), fila.getString("last_name_professional"),
				fila.getString("license_number_professional"), fila.getString("email_professional"),
				telefono == null ? "" : telefono, fila.getString("category_professional"),
				"active".equals(fila.getString("status_professional")) ? "Activo" : "Inactivo",
				fila.getString("especialidad"));
	}

	private String traducirEstado(String estado) {
		return "Activo".equals(estado) ? "active" : "inactive";
	}

}
