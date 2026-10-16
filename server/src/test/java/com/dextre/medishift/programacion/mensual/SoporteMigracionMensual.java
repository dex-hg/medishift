package com.dextre.medishift.programacion.mensual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** DDL y fixtures transaccionales: únicamente en un esquema aleatorio privado. */
abstract class SoporteMigracionMensual {

	@Autowired private DataSource origenDatos;
	protected JdbcTemplate consultas;
	protected Connection conexion;
	protected UUID institucion;
	protected UUID actor;
	protected UUID profesional;
	protected UUID consultorio;
	protected UUID especialidad;
	protected UUID legado;
	protected String esquema;
	protected final LocalDate inicio = LocalDate.of(2032, 11, 1);

	@BeforeEach
	void prepararEsquemaAislado() throws Exception {
		conexion = origenDatos.getConnection();
		try {
			conexion.setAutoCommit(false);
			esquema = "prueba_mensual_" + UUID.randomUUID().toString().replace("-", "");
			consultas = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
			assertEquals(1, consultas.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
					Integer.class), "El ensayo requiere la extensión existente, no la instala en public.");
			consultas.execute("CREATE SCHEMA " + esquema);
			consultas.execute("SET LOCAL search_path TO " + esquema + ", public");
			String base = Files.readString(Path.of("../db/medishift_postgresql.sql"))
					.replaceAll("(?m)^BEGIN;\\s*$", "")
					.replaceAll("(?m)^COMMIT;\\s*$", "")
					.replace("CREATE EXTENSION IF NOT EXISTS btree_gist;", "");
			consultas.execute(base);
			assertEquals(esquema, consultas.queryForObject("SELECT current_schema()", String.class));
			institucion = nuevaInstitucion();
			actor = nuevoActor(institucion);
			especialidad = UUID.randomUUID();
			profesional = UUID.randomUUID();
			consultorio = UUID.randomUUID();
			consultas.update("INSERT INTO specialty VALUES (?, 'E01', 'Especialidad de ensayo')", especialidad);
			consultas.update("""
					INSERT INTO professional (id_professional,id_institution,first_name_professional,
					last_name_professional,license_number_professional,category_professional,
					email_professional,status_professional) VALUES (?,?,'Persona','Temporal','P01','Medico',
					'prueba@medishift.test','active')
					""", profesional, institucion);
			consultas.update("INSERT INTO professional_specialty VALUES (?, ?, true)", profesional, especialidad);
			consultas.update("INSERT INTO room VALUES (?, ?, 'R01', 'Sala de ensayo', 'Piso 1', true, 'active')",
					consultorio, institucion);
			legado = horarioSemanal(inicio, inicio.plusDays(6), 1, "approved");
			nuevoTurno(legado, "approved");
		} catch (Exception | AssertionError fallo) {
			cerrarEsquema();
			throw fallo;
		}
	}

	@AfterEach
	void cerrarEsquema() throws SQLException {
		if (conexion != null) {
			try { conexion.rollback(); }
			finally { conexion.close(); conexion = null; }
		}
	}

	protected void migrar() throws Exception {
		ejecutarArchivo("V001__nucleo_mensual.sql");
	}

	protected void ejecutarArchivo(String archivo) throws Exception {
		consultas.execute(Files.readString(Path.of("../db/migraciones", archivo)));
	}

	protected UUID nuevaInstitucion() {
		UUID identificador = UUID.randomUUID();
		consultas.update("INSERT INTO institution VALUES (?, ?, 'Ensayo aislado', 'America/Lima', 'active')",
				identificador, identificador.toString().substring(0, 32));
		return identificador;
	}

	protected UUID nuevoActor(UUID entidad) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO user_account (id_user_account,id_institution,email_user_account,
				password_hash_user_account,status_user_account) VALUES (?,?,'ensayo@medishift.test','fixture','active')
				""", identificador, entidad);
		return identificador;
	}

	protected UUID nuevaUnidad(UUID entidad, String codigo) {
		UUID identificador = UUID.randomUUID();
		consultas.update("INSERT INTO planning_unit VALUES (?, ?, ?, 'Unidad de ensayo', 'service', 'active')",
				identificador, entidad, codigo);
		return identificador;
	}

	protected UUID horarioSemanal(LocalDate desde, LocalDate hasta, int version, String estado) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule,id_institution,period_start_schedule,period_end_schedule,
				version_schedule,status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule)
				VALUES (?,?,?,?,?,?,?,?,now())
				""", identificador, institucion, desde, hasta, version, estado, actor, actor);
		return identificador;
	}

	protected UUID planMensual(UUID unidad, LocalDate desde, LocalDate hasta, int version, String estado) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO schedule (id_schedule,id_institution,period_start_schedule,period_end_schedule,
				version_schedule,status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule,
				period_type_schedule,id_planning_unit) VALUES (?,?,?,?,?,?,?,?,now(),'monthly',?)
				""", identificador, institucion, desde, hasta, version, estado, actor, actor, unidad);
		return identificador;
	}

	protected UUID nuevoTurno(UUID horario, String estado) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO shift (id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
				start_at_shift,end_at_shift,status_shift) VALUES (?,?,?,?,?,?,?::timestamptz,?::timestamptz,?)
				""", identificador, institucion, horario, profesional, consultorio, especialidad,
				inicio + "T08:00:00-05:00", inicio + "T10:00:00-05:00", estado);
		return identificador;
	}

	protected void rechaza(String codigo, Runnable operacion) throws SQLException {
		Savepoint punto = conexion.setSavepoint();
		try {
			DataAccessException fallo = assertThrows(DataAccessException.class, operacion::run);
			Throwable causa = fallo.getMostSpecificCause();
			assertInstanceOf(SQLException.class, causa);
			assertEquals(codigo, ((SQLException) causa).getSQLState());
		} finally {
			conexion.rollback(punto);
			conexion.releaseSavepoint(punto);
		}
	}

	protected String filasOriginales() {
		return consultas.queryForObject("""
				SELECT string_agg((to_jsonb(h) - 'period_type_schedule' - 'id_planning_unit')::text,
				E'\n' ORDER BY id_schedule) FROM schedule h
				""", String.class);
	}

	protected String definiciones() {
		return consultas.queryForObject("""
				SELECT string_agg(definicion, E'\n' ORDER BY definicion) FROM (
				 SELECT table_name || ':' || column_name || ':' || data_type || ':' || is_nullable || ':' ||
				 coalesce(column_default, '') AS definicion FROM information_schema.columns WHERE table_schema = ?
				 UNION ALL SELECT c.relname || ':' || r.conname || ':' || pg_get_constraintdef(r.oid)
				 FROM pg_constraint r JOIN pg_class c ON c.oid = r.conrelid
				 JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ?
				 UNION ALL SELECT indexname || ':' || indexdef FROM pg_indexes WHERE schemaname = ?
				) definiciones
				""", String.class, esquema, esquema, esquema);
	}
}
