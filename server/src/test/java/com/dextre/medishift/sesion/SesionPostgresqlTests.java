package com.dextre.medishift.sesion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dextre.medishift.registro.RespuestaRegistro;
import com.dextre.medishift.registro.ServicioRegistro;
import com.dextre.medishift.registro.SolicitudRegistro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "medishift.prueba-sesion", matches = "true")
class SesionPostgresqlTests {

	@Autowired
	private ServicioRegistro registro;
	@Autowired
	private ServicioSesion sesiones;
	@Autowired
	private JdbcTemplate consultas;
	@Autowired
	private PlatformTransactionManager gestor;
	private final List<RespuestaRegistro> fixtures = new ArrayList<>();

	@AfterEach
	void limpiarSoloFixtures() {
		new TransactionTemplate(gestor).executeWithoutResult(estado -> {
			for (RespuestaRegistro fixture : fixtures) {
				consultas.update("DELETE FROM user_account WHERE id_user_account = ? AND id_institution = ?",
						fixture.idCuenta(), fixture.idInstitucion());
				consultas.update("DELETE FROM institution WHERE id_institution = ? AND code_institution = ?",
						fixture.idInstitucion(), fixture.codigoInstitucion());
			}
		});
		fixtures.clear();
	}

	@Test
	void autenticaCuentaRegistradaConCodigoExactoCorreoInsensibleYContrasenaExacta() {
		String contrasena = " Clave-" + UUID.randomUUID() + " 😀 ";
		RespuestaRegistro fixture = crearFixture(contrasena);
		SolicitudSesion correcta = new SolicitudSesion(" " + fixture.codigoInstitucion() + " ",
				" CUENTA@MediShift.TEST ", contrasena);
		RespuestaSesion autenticada = sesiones.autenticar(correcta);
		assertEquals(fixture.idCuenta(), autenticada.idCuenta());
		assertEquals(fixture.idInstitucion(), autenticada.idInstitucion());
		assertEquals("cuenta@medishift.test", autenticada.correo());
		assertEquals(Optional.of(autenticada),
				sesiones.consultar(fixture.idInstitucion(), fixture.idCuenta()));
		assertThrows(AccesoNoAutorizadoException.class,
				() -> sesiones.autenticar(new SolicitudSesion(
						fixture.codigoInstitucion().toUpperCase(Locale.ROOT), fixture.correo(), contrasena)));
		assertThrows(AccesoNoAutorizadoException.class,
				() -> sesiones.autenticar(new SolicitudSesion(
						fixture.codigoInstitucion(), fixture.correo(), contrasena.trim())));
	}

	@Test
	void cuentaBloqueadaOInstitucionInactivaRevocanConsultaYAcceso() {
		RespuestaRegistro fixture = crearFixture("clave-de-prueba");
		assertTrue(sesiones.consultar(fixture.idInstitucion(), fixture.idCuenta()).isPresent());
		consultas.update("UPDATE user_account SET status_user_account = 'locked' WHERE id_user_account = ?",
				fixture.idCuenta());
		assertFalse(sesiones.consultar(fixture.idInstitucion(), fixture.idCuenta()).isPresent());
		assertThrows(AccesoNoAutorizadoException.class,
				() -> sesiones.autenticar(new SolicitudSesion(
						fixture.codigoInstitucion(), fixture.correo(), "clave-de-prueba")));
		consultas.update("UPDATE user_account SET status_user_account = 'active' WHERE id_user_account = ?",
				fixture.idCuenta());
		consultas.update("UPDATE institution SET status_institution = 'inactive' WHERE id_institution = ?",
				fixture.idInstitucion());
		assertFalse(sesiones.consultar(fixture.idInstitucion(), fixture.idCuenta()).isPresent());
		assertThrows(AccesoNoAutorizadoException.class,
				() -> sesiones.autenticar(new SolicitudSesion(
						fixture.codigoInstitucion(), fixture.correo(), "clave-de-prueba")));
	}

	@Test
	void mismoCorreoSeAutenticaEnSuInstitucionSinCruzarCuentas() {
		RespuestaRegistro primera = crearFixture("clave-primera");
		RespuestaRegistro segunda = crearFixture("clave-segunda");
		RespuestaSesion sesionPrimera = sesiones.autenticar(new SolicitudSesion(
				primera.codigoInstitucion(), primera.correo(), "clave-primera"));
		RespuestaSesion sesionSegunda = sesiones.autenticar(new SolicitudSesion(
				segunda.codigoInstitucion(), segunda.correo(), "clave-segunda"));
		assertEquals(primera.idCuenta(), sesionPrimera.idCuenta());
		assertEquals(segunda.idCuenta(), sesionSegunda.idCuenta());
		assertThrows(AccesoNoAutorizadoException.class,
				() -> sesiones.autenticar(new SolicitudSesion(
						segunda.codigoInstitucion(), segunda.correo(), "clave-primera")));
	}

	private RespuestaRegistro crearFixture(String contrasena) {
		String codigo = "T-sesion-" + UUID.randomUUID().toString().replace("-", "").substring(0, 23);
		RespuestaRegistro fixture = registro.registrar(new SolicitudRegistro(codigo,
				"Institución de prueba de sesión", "America/Lima", "CUENTA@MediShift.TEST", contrasena));
		fixtures.add(fixture);
		return fixture;
	}

}
