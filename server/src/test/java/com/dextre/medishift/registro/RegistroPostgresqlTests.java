package com.dextre.medishift.registro;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "medishift.prueba-registro", matches = "true")
class RegistroPostgresqlTests extends SoporteCuentasPostgresql {

	private final List<UUID> institucionesPrueba = new ArrayList<>();
	private RepositorioRegistro repositorio;
	private ServicioRegistro servicio;

	@BeforeEach
	void prepararRegistro() {
		repositorio = spy(new RepositorioRegistro(consultas));
		doAnswer(invocacion -> {
			institucionesPrueba.add(invocacion.getArgument(0));
			return invocacion.callRealMethod();
		}).when(repositorio).insertarInstitucion(any(), any());
		servicio = new ServicioRegistro(repositorio, accesos, codificador, gestor);
	}

	@Test
	void persisteInstitucionCuentaActivasHashYFechaDelServidor() {
		String codigo = codigoPrueba();
		String contrasena = " clave-de-prueba-" + UUID.randomUUID() + " ";
		RespuestaRegistro respuesta = servicio.registrar(solicitud(codigo, contrasena));
		assertEquals(codigo, respuesta.codigoInstitucion());
		assertEquals("cuenta@medishift.test", respuesta.correo());
		assertEquals("active", consultas.queryForObject(
				"SELECT status_institution FROM institution WHERE id_institution = ?",
				String.class, respuesta.idInstitucion()));
		assertEquals("active", consultas.queryForObject(
				"SELECT status_user_account FROM user_account WHERE id_user_account = ?",
				String.class, respuesta.idCuenta()));
		String hash = consultas.queryForObject(
				"SELECT password_hash_user_account FROM user_account WHERE id_user_account = ?",
				String.class, respuesta.idCuenta());
		assertNotNull(hash);
		assertTrue(hash.startsWith("{pbkdf2@medishift-v1}"));
		assertFalse(hash.equals(contrasena));
		assertTrue(codificador.matches(contrasena, hash));
		assertFalse(codificador.matches(contrasena.trim(), hash));
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM user_account
				WHERE id_user_account = ? AND id_institution = ? AND created_at_user_account IS NOT NULL
				""", Integer.class, respuesta.idCuenta(), respuesta.idInstitucion()));
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM user_role ur JOIN role r ON r.id_role = ur.id_role
				WHERE ur.id_user_account = ? AND r.code_role = 'administrador'
				""", Integer.class, respuesta.idCuenta()));
		assertEquals(1, consultas.queryForObject("""
				SELECT count(*) FROM audit_event WHERE id_institution = ?
				AND id_actor_user_account = ? AND action_audit_event = 'register_admin'
				AND after_audit_event->>'origen' = 'registro_institucion'
				""", Integer.class, respuesta.idInstitucion(), respuesta.idCuenta()));
	}

	@Test
	void duplicadoNoGeneraInstitucionNiCuentaAdicional() {
		String codigo = codigoPrueba();
		RespuestaRegistro respuesta = servicio.registrar(solicitud(codigo, "clave-de-prueba"));
		assertThrows(InstitucionDuplicadaException.class,
				() -> servicio.registrar(solicitud(codigo, "otra-clave-de-prueba")));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM institution WHERE code_institution = ?",
				Integer.class, codigo));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM user_account WHERE id_institution = ?",
				Integer.class, respuesta.idInstitucion()));
	}

	@Test
	void falloDelSegundoInsertRevierteLaInstitucion() {
		String codigo = codigoPrueba();
		PasswordEncoder codificadorInvalido = mock(PasswordEncoder.class);
		when(codificadorInvalido.encode(any())).thenReturn(" ");
		ServicioRegistro servicioConFallo = new ServicioRegistro(repositorio, accesos, codificadorInvalido, gestor);
		assertThrows(DataIntegrityViolationException.class,
				() -> servicioConFallo.registrar(solicitud(codigo, "clave-de-prueba")));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM institution WHERE code_institution = ?",
				Integer.class, codigo));
		for (UUID idInstitucion : institucionesPrueba) {
			assertEquals(0, consultas.queryForObject("SELECT count(*) FROM user_account WHERE id_institution = ?",
						Integer.class, idInstitucion));
		}
	}

	@Test
	void permiteMismoCorreoEnInstitucionesDiferentes() {
		RespuestaRegistro primera = servicio.registrar(solicitud(codigoPrueba(), "primera-clave"));
		RespuestaRegistro segunda = servicio.registrar(solicitud(codigoPrueba(), "segunda-clave"));
		assertEquals(2, consultas.queryForObject("""
				SELECT count(*) FROM user_account
				WHERE id_institution IN (?, ?) AND email_user_account = 'cuenta@medishift.test'
				""", Integer.class, primera.idInstitucion(), segunda.idInstitucion()));
	}

	@Test
	void falloDeAuditoriaRevierteInstitucionCuentaRolYVinculo() {
		consultas.execute("""
				CREATE FUNCTION rechazar_alta_de_prueba() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'Fallo de prueba' USING ERRCODE = 'P0001'; END; $$;
				CREATE TRIGGER rechazar_alta_de_prueba BEFORE INSERT ON audit_event
				FOR EACH ROW EXECUTE FUNCTION rechazar_alta_de_prueba();
				""");
		assertThrows(org.springframework.dao.DataAccessException.class,
				() -> servicio.registrar(solicitud(codigoPrueba(), "clave-de-prueba")));
		for (String tabla : List.of("institution", "user_account", "role", "user_role", "audit_event")) {
			assertEquals(0, consultas.queryForObject("SELECT count(*) FROM " + tabla, Integer.class), tabla);
		}
	}

	private String codigoPrueba() {
		return "T-reg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
	}

	private SolicitudRegistro solicitud(String codigo, String contrasena) {
		return new SolicitudRegistro(codigo, "Institución de prueba", "America/Lima",
				" CUENTA@MediShift.TEST ", contrasena);
	}

}
