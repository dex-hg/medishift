package com.dextre.medishift.catalogos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class SoporteCatalogosPostgresql {

	@Autowired
	protected JdbcTemplate consultas;
	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private PlatformTransactionManager gestor;
	protected MockMvc cliente;
	protected CuentaPrueba cuenta;
	protected MockHttpSession sesion;
	private final List<CuentaPrueba> cuentas = new ArrayList<>();
	private final List<UUID> especialidades = new ArrayList<>();
	private final List<String> nombresNuevos = new ArrayList<>();

	@BeforeEach
	void prepararClienteYCuentaTemporal() {
		cliente = MockMvcBuilders.webAppContextSetup(contexto)
				.addFilters(contexto.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
		cuenta = crearCuenta();
		sesion = sesionDe(cuenta);
	}

	@AfterEach
	void eliminarExclusivamenteFixturesPorUuid() {
		new TransactionTemplate(gestor).executeWithoutResult(estado -> {
			for (CuentaPrueba fixture : cuentas) {
				UUID institucion = fixture.institucion();
				consultas.update("DELETE FROM shift WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM schedule WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM availability WHERE id_professional IN "
						+ "(SELECT id_professional FROM professional WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM professional_specialty WHERE id_professional IN "
						+ "(SELECT id_professional FROM professional WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM professional WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM room_block WHERE id_room IN "
						+ "(SELECT id_room FROM room WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM room_specialty WHERE id_room IN "
						+ "(SELECT id_room FROM room WHERE id_institution = ?)", institucion);
				consultas.update("DELETE FROM room WHERE id_institution = ?", institucion);
				consultas.update("DELETE FROM user_account WHERE id_user_account = ? AND id_institution = ?",
						fixture.cuenta(), institucion);
				consultas.update("DELETE FROM institution WHERE id_institution = ?", institucion);
			}
			for (String nombre : nombresNuevos) {
				especialidades.addAll(consultas.query("SELECT id_specialty FROM specialty WHERE name_specialty = ?",
						(fila, indice) -> fila.getObject(1, UUID.class), nombre));
			}
			for (UUID especialidad : especialidades) {
				consultas.update("DELETE FROM specialty WHERE id_specialty = ?", especialidad);
			}
		});
		cuentas.clear();
		especialidades.clear();
		nombresNuevos.clear();
	}

	protected CuentaPrueba crearCuenta() {
		CuentaPrueba fixture = new CuentaPrueba(UUID.randomUUID(), UUID.randomUUID());
		cuentas.add(fixture);
		new TransactionTemplate(gestor).executeWithoutResult(estado -> {
			consultas.update("""
					INSERT INTO institution VALUES (?, ?, ?, 'America/Lima', 'active')
					""", fixture.institucion(), "T-cat-" + fixture.institucion().toString().substring(0, 24),
					"Institución temporal de catálogo");
			consultas.update("""
					INSERT INTO user_account (id_user_account, id_institution, email_user_account,
					password_hash_user_account, status_user_account)
					VALUES (?, ?, 'catalogo@medishift.test', 'hash-fixture-sin-acceso', 'active')
					""", fixture.cuenta(), fixture.institucion());
		});
		return fixture;
	}

	protected MockHttpSession sesionDe(CuentaPrueba fixture) {
		MockHttpSession resultado = new MockHttpSession();
		resultado.setAttribute("medishift.idCuenta", fixture.cuenta());
		resultado.setAttribute("medishift.idInstitucion", fixture.institucion());
		return resultado;
	}

	protected String crearEspecialidad() {
		UUID id = UUID.randomUUID();
		especialidades.add(id);
		String nombre = "Especialidad prueba " + id;
		consultas.update("INSERT INTO specialty VALUES (?, ?, ?)",
				id, "T-cat-" + id.toString().substring(0, 24), nombre);
		return nombre;
	}

	protected String nuevaEspecialidad() {
		String nombre = "Nueva especialidad " + UUID.randomUUID();
		nombresNuevos.add(nombre);
		return nombre;
	}

	protected UUID idEspecialidad(String nombre) {
		return consultas.queryForObject("SELECT id_specialty FROM specialty WHERE name_specialty = ?",
				UUID.class, nombre);
	}

	protected UUID crearRegistro(String ruta, String contenido) throws Exception {
		String respuesta = cliente.perform(json(post(ruta), contenido))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(respuesta, "$.id"));
	}

	protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder solicitud, String contenido) {
		return solicitud.session(sesion).contentType(MediaType.APPLICATION_JSON).content(contenido);
	}

	protected String profesional(String colegiatura, String nombre, String especialidad) {
		return """
				{"nombres":"%s","apellidos":"Pérez Díaz","colegiatura":"%s",
				 "correo":" %s@MediShift.TEST ","telefono":"+51 999888777",
				 "categoria":"Médico","estado":"Activo","especialidad":"%s"}
				""".formatted(nombre, colegiatura, colegiatura.trim().toLowerCase(Locale.ROOT), especialidad);
	}

	protected String consultorio(String codigo, String especialidad) {
		return """
				{"codigo":"%s","nombre":"Consultorio temporal","ubicacion":"Primer piso",
				 "especialidad":"%s","estado":"Activo"}
				""".formatted(codigo, especialidad);
	}

	protected record CuentaPrueba(UUID institucion, UUID cuenta) {
	}
}
