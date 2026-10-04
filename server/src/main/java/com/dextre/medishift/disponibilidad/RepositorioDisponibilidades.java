package com.dextre.medishift.disponibilidad;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.SolicitudDisponibilidad;

@Repository
public class RepositorioDisponibilidades {

	private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
	private static final String CONSULTA = """
			SELECT a.*, concat_ws(' ', p.first_name_professional, p.last_name_professional) AS profesional
			FROM availability a JOIN professional p ON p.id_professional = a.id_professional
			WHERE p.id_institution = ?
			""";
	private final JdbcTemplate consultas;

	public RepositorioDisponibilidades(JdbcTemplate consultas) {
		this.consultas = consultas;
	}

	public List<Disponibilidad> listar(UUID institucion) {
		return consultas.query(CONSULTA + " ORDER BY lower(concat_ws(' ', p.first_name_professional, p.last_name_professional)), a.weekday_availability, "
				+ "a.valid_from_availability, a.start_time_availability, a.id_availability",
				(fila, numero) -> leer(fila), institucion);
	}

	public Optional<Disponibilidad> buscar(UUID institucion, UUID identificador) {
		return consultas.query(CONSULTA + " AND a.id_availability = ?",
				(fila, numero) -> leer(fila), institucion, identificador).stream().findFirst();
	}

	public void verificarCruces(UUID institucion, UUID identificador, SolicitudDisponibilidad solicitud) {
		if (!"Activo".equals(solicitud.estado())) {
			return;
		}
		List<Disponibilidad> existentes = consultas.query(CONSULTA + """
				 AND a.id_professional = ? AND a.weekday_availability = ?
				 AND a.status_availability = 'active'
				 AND a.valid_from_availability <= ? AND a.valid_to_availability >= ?
				 AND a.start_time_availability < ? AND a.end_time_availability > ?
				""", (fila, numero) -> leer(fila), institucion, solicitud.idProfesional(), solicitud.diaSemana(),
				Date.valueOf(solicitud.fechaFin()), Date.valueOf(solicitud.fechaInicio()),
				Time.valueOf(solicitud.horaFin()), Time.valueOf(solicitud.horaInicio()));
		for (Disponibilidad existente : existentes) {
			if (existente.id().equals(identificador)) {
				continue;
			}
			LocalDate inicioExistente = LocalDate.parse(existente.fechaInicio());
			LocalDate finExistente = LocalDate.parse(existente.fechaFin());
			LocalDate inicioComun = inicioExistente.isAfter(solicitud.fechaInicio())
					? inicioExistente : solicitud.fechaInicio();
			LocalDate finComun = finExistente.isBefore(solicitud.fechaFin()) ? finExistente : solicitud.fechaFin();
			if (ValidadorDisponibilidades.tieneOcurrencia(inicioComun, finComun, solicitud.diaSemana())) {
				throw CatalogoException.conflicto("horaInicio",
						"La disponibilidad se cruza con otra ventana activa del profesional para ese día y vigencia.");
			}
		}
	}

	public void insertar(UUID institucion, UUID identificador, SolicitudDisponibilidad solicitud) {
		int insertados = consultas.update("""
				INSERT INTO availability (id_availability, id_professional, weekday_availability,
				    start_time_availability, end_time_availability, valid_from_availability,
				    valid_to_availability, status_availability, note_availability)
				SELECT ?, p.id_professional, ?, ?, ?, ?, ?, ?, ?
				FROM professional p WHERE p.id_institution = ? AND p.id_professional = ?
				""", identificador, solicitud.diaSemana(), Time.valueOf(solicitud.horaInicio()),
				Time.valueOf(solicitud.horaFin()), Date.valueOf(solicitud.fechaInicio()), Date.valueOf(solicitud.fechaFin()),
				traducirEstado(solicitud.estado()), solicitud.observacion().isEmpty() ? null : solicitud.observacion(),
				institucion, solicitud.idProfesional());
		if (insertados == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	public void actualizar(UUID institucion, UUID identificador, SolicitudDisponibilidad solicitud) {
		int modificados = consultas.update("""
				UPDATE availability a SET id_professional = ?, weekday_availability = ?,
				    start_time_availability = ?, end_time_availability = ?, valid_from_availability = ?,
				    valid_to_availability = ?, status_availability = ?, note_availability = ?
				FROM professional anterior, professional nuevo
				WHERE a.id_professional = anterior.id_professional AND anterior.id_institution = ?
				    AND nuevo.id_professional = ? AND nuevo.id_institution = anterior.id_institution
				    AND a.id_availability = ?
				""", solicitud.idProfesional(), solicitud.diaSemana(), Time.valueOf(solicitud.horaInicio()),
				Time.valueOf(solicitud.horaFin()), Date.valueOf(solicitud.fechaInicio()), Date.valueOf(solicitud.fechaFin()),
				traducirEstado(solicitud.estado()), solicitud.observacion().isEmpty() ? null : solicitud.observacion(),
				institucion, solicitud.idProfesional(), identificador);
		if (modificados == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	public void eliminar(UUID institucion, UUID identificador) {
		if (consultas.update("""
				DELETE FROM availability a USING professional p
				WHERE a.id_professional = p.id_professional AND p.id_institution = ? AND a.id_availability = ?
				""", institucion, identificador) == 0) {
			throw CatalogoException.noEncontrado();
		}
	}

	private Disponibilidad leer(ResultSet fila) throws SQLException {
		String observacion = fila.getString("note_availability");
		Disponibilidad disponibilidad = new Disponibilidad(fila.getObject("id_availability", UUID.class),
				fila.getObject("id_professional", UUID.class), fila.getString("profesional"),
				fila.getInt("weekday_availability"),
				fila.getObject("start_time_availability", LocalTime.class).format(FORMATO_HORA),
				fila.getObject("end_time_availability", LocalTime.class).format(FORMATO_HORA),
				fila.getObject("valid_from_availability", LocalDate.class).toString(),
				fila.getObject("valid_to_availability", LocalDate.class).toString(),
				"active".equals(fila.getString("status_availability")) ? "Activo" : "Inactivo",
				observacion == null ? "" : observacion, "");
		return new Disponibilidad(disponibilidad.id(), disponibilidad.idProfesional(), disponibilidad.profesional(),
				disponibilidad.diaSemana(), disponibilidad.horaInicio(), disponibilidad.horaFin(),
				disponibilidad.fechaInicio(), disponibilidad.fechaFin(), disponibilidad.estado(),
				disponibilidad.observacion(), RevisionDisponibilidades.calcular(disponibilidad));
	}

	private String traducirEstado(String estado) {
		return "Activo".equals(estado) ? "active" : "inactive";
	}

}
