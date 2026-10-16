package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class AccesoMensualPostgresqlTests extends SoporteApiPlanesMensuales {

	@Test
	void inicializaConfirmaYRepiteSinDuplicarRolVinculoNiAuditoria() throws Exception {
		assertTrue(inicializar(institucion, actor));
		assertFalse(inicializar(institucion, actor));
		assertEquals(1, contar("role"));
		assertEquals(1, contar("user_role"));
		assertEquals(1, contar("audit_event"));
		assertEquals("administrador", consultas.queryForObject("SELECT code_role FROM role", String.class));
		assertTrue(consultas.queryForObject("""
				SELECT id_actor_user_account IS NULL AND action_audit_event = 'bootstrap_admin'
				 AND after_audit_event->>'institucion' = ? AND after_audit_event->>'cuenta' = ?
				 AND after_audit_event->>'rol' = 'administrador' AND after_audit_event->>'origen' = 'local'
				 AND (SELECT count(*) FROM jsonb_object_keys(after_audit_event)) = 4
				FROM audit_event
				""", Boolean.class, institucion.toString(), actor.toString()));
	}

	@Test
	void reutilizaElRolGlobalSinCompartirLaPertenenciaInstitucional() throws Exception {
		assertTrue(inicializar(institucion, actor));
		UUID otraInstitucion = nuevaInstitucion();
		UUID otraCuenta = nuevaCuenta(otraInstitucion);
		assertTrue(inicializar(otraInstitucion, otraCuenta));
		assertEquals(1, contar("role"));
		assertEquals(2, contar("user_role"));
		CatalogoException fallo = assertThrows(CatalogoException.class,
				() -> aislar(() -> { accesos.exigirAdministrador(institucion, otraCuenta); return null; }));
		assertEquals(HttpStatus.FORBIDDEN, fallo.obtenerEstado());
	}

	@Test
	void rechazaAgregarOtroAdministradorYConservaElPrimero() throws Exception {
		inicializar(institucion, actor);
		UUID segunda = nuevaCuenta(institucion);
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> inicializar(institucion, segunda));
		assertEquals(HttpStatus.CONFLICT, fallo.obtenerEstado());
		assertEquals(1, contar("user_role"));
		assertEquals(actor, consultas.queryForObject("SELECT id_user_account FROM user_role", UUID.class));
		assertEquals(1, contar("audit_event"));
	}

	@Test
	void rechazaCuentaAjenaYCuentaOInstitucionInactivasSinAsignarRoles() {
		UUID ajena = nuevaInstitucion();
		UUID cuentaAjena = nuevaCuenta(ajena);
		CatalogoException ajenaDenegada = assertThrows(CatalogoException.class,
				() -> inicializar(institucion, cuentaAjena));
		assertEquals(HttpStatus.FORBIDDEN, ajenaDenegada.obtenerEstado());
		consultas.update("UPDATE user_account SET status_user_account = 'inactive' WHERE id_user_account = ?", actor);
		assertThrows(CatalogoException.class, () -> inicializar(institucion, actor));
		consultas.update("UPDATE user_account SET status_user_account = 'active' WHERE id_user_account = ?", actor);
		consultas.update("UPDATE institution SET status_institution = 'inactive' WHERE id_institution = ?", institucion);
		assertThrows(CatalogoException.class, () -> inicializar(institucion, actor));
		assertEquals(0, contar("role"));
		assertEquals(0, contar("user_role"));
		assertEquals(0, contar("audit_event"));
	}

	@Test
	void laRevocacionYElEstadoActivoSeRevalidanEnCadaTransaccion() throws Exception {
		inicializar(institucion, actor);
		aislar(() -> { accesos.exigirAdministrador(institucion, actor); return null; });
		consultas.update("UPDATE user_account SET status_user_account = 'locked' WHERE id_user_account = ?", actor);
		assertThrows(CatalogoException.class,
				() -> aislar(() -> { accesos.exigirAdministrador(institucion, actor); return null; }));
		consultas.update("UPDATE user_account SET status_user_account = 'active' WHERE id_user_account = ?", actor);
		consultas.update("DELETE FROM user_role WHERE id_user_account = ?", actor);
		assertThrows(CatalogoException.class,
				() -> aislar(() -> { accesos.exigirAdministrador(institucion, actor); return null; }));
	}

	@Test
	void elFalloDeAuditoriaRevierteElRolNuevoYLaAsignacion() {
		fallarAuditoria("bootstrap_admin");
		assertThrows(org.springframework.dao.DataAccessException.class, () -> inicializar(institucion, actor));
		assertEquals(0, contar("role"));
		assertEquals(0, contar("user_role"));
		assertEquals(0, contar("audit_event"));
	}

	@Test
	void controlaElAgotamientoDeIdentificadoresSmallintSinCambiosParciales() {
		consultas.update("INSERT INTO role VALUES (32767, 'otro', 'Rol previo de ensayo')");
		CatalogoException fallo = assertThrows(CatalogoException.class, () -> inicializar(institucion, actor));
		assertEquals(HttpStatus.CONFLICT, fallo.obtenerEstado());
		assertEquals(1, contar("role"));
		assertEquals(0, contar("user_role"));
		assertEquals(0, contar("audit_event"));
	}

	@Test
	void dosInicializacionesConcurrentesSoloAsignanUnAdministrador() throws Exception {
		UUID segunda = nuevaCuenta(institucion);
		CountDownLatch inicioConcurrente = new CountDownLatch(1);
		var ejecutor = Executors.newFixedThreadPool(2);
		try {
			var primera = ejecutor.submit(() -> inicializarAlMismoTiempo(actor, inicioConcurrente));
			var otra = ejecutor.submit(() -> inicializarAlMismoTiempo(segunda, inicioConcurrente));
			inicioConcurrente.countDown();
			assertEquals(List.of("alta", "conflicto"), List.of(primera.get(20, TimeUnit.SECONDS),
					otra.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
			assertEquals(1, contar("role"));
			assertEquals(1, contar("user_role"));
			assertEquals(1, contar("audit_event"));
		} finally {
			ejecutor.shutdownNow();
			assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private String inicializarAlMismoTiempo(UUID cuenta, CountDownLatch comienzo) throws Exception {
		assertTrue(comienzo.await(10, TimeUnit.SECONDS));
		try {
			assertTrue(inicializar(institucion, cuenta));
			return "alta";
		} catch (CatalogoException fallo) {
			assertEquals(HttpStatus.CONFLICT, fallo.obtenerEstado());
			return "conflicto";
		}
	}
}
