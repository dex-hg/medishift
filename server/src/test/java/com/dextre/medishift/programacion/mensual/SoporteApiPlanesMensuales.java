package com.dextre.medishift.programacion.mensual;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import com.dextre.medishift.acceso.ServicioAccesoMensual;

import static org.junit.jupiter.api.Assertions.*;

/** Cada servicio confirma o revierte su transacción real en un esquema privado. */
abstract class SoporteApiPlanesMensuales extends SoporteMigracionMensual {

	@Autowired private DataSource origenMensual;
	@Autowired private WebApplicationContext contexto;
	@Autowired protected ServicioAccesoMensual accesos;
	protected MockMvc cliente;
	protected MockHttpSession sesion;

	@BeforeEach
	void prepararApiMensual() throws Exception {
		migrar();
		conexion.commit();
		conexion.setAutoCommit(true);
		consultas.queryForObject("SELECT set_config('search_path', ?, false)", String.class, esquema + ", public");
		assertEquals(esquema, consultas.queryForObject("SELECT current_schema()", String.class));
		cliente = MockMvcBuilders.webAppContextSetup(contexto)
				.addFilters(contexto.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
		sesion = sesionDe(institucion, actor);
	}

	protected MockHttpSession sesionDe(UUID entidad, UUID cuenta) {
		MockHttpSession resultado = new MockHttpSession();
		resultado.setAttribute("medishift.idInstitucion", entidad);
		resultado.setAttribute("medishift.idCuenta", cuenta);
		return resultado;
	}

	protected UUID nuevaCuenta(UUID entidad) {
		UUID identificador = UUID.randomUUID();
		consultas.update("""
				INSERT INTO user_account (id_user_account,id_institution,email_user_account,
				password_hash_user_account,status_user_account) VALUES (?, ?, ?, 'hash-privado-de-ensayo', 'active')
				""", identificador, entidad, identificador + "@medishift.test");
		return identificador;
	}

	protected boolean inicializar(UUID entidad, UUID cuenta) throws Exception {
		return aislar(() -> accesos.inicializarAdministrador(entidad, cuenta));
	}

	protected ResultActions solicitar(MockHttpServletRequestBuilder solicitud) throws Exception {
		return aislar(() -> cliente.perform(solicitud));
	}

	/** No se abre una transacción de prueba: la inicia el proxy real del servicio. */
	protected <T> T aislar(Callable<T> operacion) throws Exception {
		assertFalse(TransactionSynchronizationManager.hasResource(origenMensual));
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
		try (Connection privada = origenMensual.getConnection()) {
			privada.setAutoCommit(true);
			JdbcTemplate conexionPrivada = new JdbcTemplate(new SingleConnectionDataSource(privada, true));
			conexionPrivada.queryForObject("SELECT set_config('search_path', ?, false)", String.class, esquema + ", public");
			assertEquals(esquema, conexionPrivada.queryForObject("SELECT current_schema()", String.class));
			TransactionSynchronizationManager.bindResource(origenMensual, new ConnectionHolder(privada));
			try {
				return operacion.call();
			} finally {
				TransactionSynchronizationManager.unbindResource(origenMensual);
				if (!privada.getAutoCommit()) {
					privada.rollback();
					privada.setAutoCommit(true);
				}
				conexionPrivada.execute("RESET search_path");
			}
		}
	}

	protected int contar(String tabla) {
		assertTrue(java.util.Set.of("planning_unit", "schedule", "role", "user_role", "audit_event").contains(tabla));
		return consultas.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
	}

	protected void fallarAuditoria(String accion) {
		assertTrue(java.util.Set.of("create", "bootstrap_admin").contains(accion));
		consultas.execute("""
				CREATE FUNCTION fallar_auditoria_de_ensayo() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
				 IF NEW.action_audit_event = '%s' THEN
				  RAISE EXCEPTION 'Fallo controlado de auditoría' USING ERRCODE = 'P0001';
				 END IF;
				 RETURN NEW;
				END; $$;
				CREATE TRIGGER fallar_auditoria_de_ensayo BEFORE INSERT ON audit_event
				FOR EACH ROW EXECUTE FUNCTION fallar_auditoria_de_ensayo();
				""".formatted(accion));
	}

	@Override
	@AfterEach
	void cerrarEsquema() throws SQLException {
		if (conexion == null) { return; }
		try {
			if (!conexion.getAutoCommit()) { conexion.rollback(); conexion.setAutoCommit(true); }
			if (esquema == null || !esquema.matches("prueba_mensual_[0-9a-f]{32}")) {
				throw new IllegalStateException("Se rechazó la limpieza de un esquema sin identidad de ensayo.");
			}
			try (var instruccion = conexion.createStatement()) {
				instruccion.execute("RESET search_path");
				instruccion.execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
			}
		} finally {
			conexion.close();
			conexion = null;
		}
	}
}
