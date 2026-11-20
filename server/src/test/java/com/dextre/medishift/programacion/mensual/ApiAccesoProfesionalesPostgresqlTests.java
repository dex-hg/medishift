package com.dextre.medishift.programacion.mensual;

import java.util.Locale;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiAccesoProfesionalesPostgresqlTests extends SoporteApiPlanesMensuales {

	@BeforeEach
	void habilitarAdministrador() throws Exception {
		assertTrue(inicializar(institucion, actor));
	}

	@Test
	void registraAlMedicoConAccesoYCambiaSusCredenciales() throws Exception {
		String correoInicial = "inicial@medishift.test";
		String claveInicial = " Clave ficticia inicial ";
		var alta = json(post("/api/profesionales"), ficha("P-NEW", """
				,"correoAcceso":"INICIAL@medishift.test","contrasenaAcceso":"%s"
				""".formatted(claveInicial))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.correo").value("p-new@medishift.test"))
				.andExpect(jsonPath("$.contrasenaAcceso").doesNotExist()).andReturn();
		String ruta = rutaAcceso(alta);
		var acceso = solicitar(get(ruta).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitado").value(true)).andReturn();
		iniciarSesion(correoInicial, claveInicial).andExpect(status().isOk()).andExpect(jsonPath("$.esDoctor").value(true));

		String correoNuevo = "renovado@medishift.test";
		String claveNueva = "Clave ficticia renovada";
		String cambio = """
				{"correo":"%s","contrasena":"%s"}
				""".formatted(correoNuevo, claveNueva);
		json(put(ruta).header("If-Match", acceso.getResponse().getHeader("ETag")), cambio)
				.andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(correoNuevo))
				.andExpect(jsonPath("$.habilitado").value(true)).andExpect(jsonPath("$.contrasena").doesNotExist());
		iniciarSesion(correoInicial, claveInicial).andExpect(status().isUnauthorized());
		iniciarSesion(correoNuevo, claveInicial).andExpect(status().isUnauthorized());
		iniciarSesion(correoNuevo, claveNueva).andExpect(status().isOk()).andExpect(jsonPath("$.esDoctor").value(true));
		json(put(ruta).header("If-Match", acceso.getResponse().getHeader("ETag")), cambio).andExpect(status().isConflict());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE after_audit_event::text LIKE '%Clave ficticia%'", Integer.class));
	}

	@Test
	void accesoEsOpcionalYElCorreoDuplicadoRevierteElAlta() throws Exception {
		var alta = json(post("/api/profesionales"), ficha("SIN-CUENTA", ""))
				.andExpect(status().isCreated()).andReturn();
		solicitar(get(rutaAcceso(alta)).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.idCuenta").isEmpty()).andExpect(jsonPath("$.habilitado").value(false));
		json(post("/api/profesionales"), ficha("NO-CREADO", """
				,"correoAcceso":"ENSAYO@medishift.test","contrasenaAcceso":"Clave ficticia de ensayo"
				""")).andExpect(status().isConflict()).andExpect(jsonPath("$.errores.correoAcceso").exists());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM professional WHERE license_number_professional='NO-CREADO'", Integer.class));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM user_account", Integer.class));
	}

	@Test
	void protegeLasCredencialesDeUnaCuentaConRolAdministrativo() throws Exception {
		consultas.update("UPDATE professional SET id_user_account=? WHERE id_professional=?", actor, profesional);
		String ruta = "/api/profesionales/" + profesional + "/acceso";
		var acceso = solicitar(get(ruta).session(sesion)).andExpect(status().isOk())
				.andExpect(jsonPath("$.credencialesEditables").value(false)).andReturn();
		json(put(ruta).header("If-Match", acceso.getResponse().getHeader("ETag")), """
				{"correo":"ensayo@medishift.test","contrasena":"Clave ficticia administrativa"}
				""").andExpect(status().isForbidden());
	}

	private String rutaAcceso(MvcResult alta) throws Exception {
		UUID id = UUID.fromString(JsonPath.read(alta.getResponse().getContentAsString(), "$.id"));
		return "/api/profesionales/" + id + "/acceso";
	}

	private ResultActions iniciarSesion(String correo, String clave) throws Exception {
		String codigo = consultas.queryForObject("SELECT code_institution FROM institution WHERE id_institution=?", String.class, institucion);
		return solicitar(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content("""
				{"codigoInstitucion":"%s","correo":"%s","contrasena":"%s"}
				""".formatted(codigo, correo, clave)));
	}

	private ResultActions json(MockHttpServletRequestBuilder solicitud, String cuerpo) throws Exception {
		return solicitar(solicitud.session(sesion).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private String ficha(String colegiatura, String acceso) {
		return """
				{"nombres":"Ana","apellidos":"Ejemplo","colegiatura":"%s","correo":"%s@medishift.test",
				"categoria":"Médico","estado":"Activo","idsEtiquetas":["%s"]%s}
				""".formatted(colegiatura, colegiatura.toLowerCase(Locale.ROOT), especialidad, acceso);
	}
}
