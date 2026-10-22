package com.dextre.medishift.programacion.mensual;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class RegistroPlanificacionPostgresqlTests extends SoporteApiPlanesMensuales {

	@Test
	void registroLoginYPlanMensualFuncionanSinInicializacionManual() throws Exception {
		String codigo = "REG-" + UUID.randomUUID().toString().substring(0, 12);
		String alta = solicitar(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"codigoInstitucion":"%s","nombreInstitucion":"Clínica de prueba",
				 "zonaHoraria":"America/Lima","correo":"admin@medishift.test","contrasena":"clave-de-prueba"}
				""".formatted(codigo))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID cuenta = UUID.fromString(JsonPath.read(alta, "$.idCuenta"));
		UUID entidad = UUID.fromString(JsonPath.read(alta, "$.idInstitucion"));
		var acceso = solicitar(post("/api/sesion").contentType(MediaType.APPLICATION_JSON).content("""
				{"codigoInstitucion":"%s","correo":"admin@medishift.test","contrasena":"clave-de-prueba"}
				""".formatted(codigo))).andExpect(status().isOk())
				.andExpect(jsonPath("$.esAdministrador").value(true)).andReturn();
		MockHttpSession sesionNueva = (MockHttpSession) acceso.getRequest().getSession(false);
		assertNotNull(sesionNueva);
		solicitar(get("/api/sesion").session(sesionNueva)).andExpect(status().isOk())
				.andExpect(jsonPath("$.esAdministrador").value(true));
		solicitar(get("/api/unidades-planificacion").session(sesionNueva)).andExpect(status().isOk());
		String unidad = solicitar(post("/api/unidades-planificacion").session(sesionNueva)
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"codigo":"CONSULTA","nombre":"Consulta externa","tipo":"Servicio"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String idUnidad = JsonPath.read(unidad, "$.id");
		solicitar(post("/api/planes-mensuales").session(sesionNueva).contentType(MediaType.APPLICATION_JSON)
				.content("""
				{"idUnidad":"%s","periodo":"2032-11"}
				""".formatted(idUnidad))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("Borrador"));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM user_role WHERE id_user_account = ?",
				Integer.class, cuenta));
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE id_institution = ? "
				+ "AND action_audit_event = 'register_admin'", Integer.class, entidad));
	}

	@Test
	void registrarUnCodigoExistenteNoOtorgaPermisosALasCuentasPrevias() throws Exception {
		String codigo = consultas.queryForObject("SELECT code_institution FROM institution WHERE id_institution = ?",
				String.class, institucion);
		solicitar(post("/api/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"codigoInstitucion":"%s","nombreInstitucion":"Intento duplicado",
				 "zonaHoraria":"America/Lima","correo":"otra@medishift.test","contrasena":"clave-de-prueba"}
				""".formatted(codigo))).andExpect(status().isConflict());
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM user_role WHERE id_user_account = ?",
				Integer.class, actor));
		solicitar(get("/api/unidades-planificacion").session(sesion)).andExpect(status().isForbidden());
	}
}
