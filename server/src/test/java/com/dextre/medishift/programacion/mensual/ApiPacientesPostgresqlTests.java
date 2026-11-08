package com.dextre.medishift.programacion.mensual;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiPacientesPostgresqlTests extends SoporteAtencionPostgresql {
    @Test
    void registraNormalizaRechazaDuplicadosYConservaCodigoAlActualizar() throws Exception {
        var creado = paciente(sesion, "12345678");
        String id = texto(creado, "id");
        assertEquals(20, texto(creado, "codigo").length());
        solicitar(post("/api/pacientes").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datosPaciente("12345678", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.dni").exists());
        var actualizado = solicitar(put("/api/pacientes/" + id).session(sesion).header("If-Match", creado.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content(datosPaciente("12345678", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false)).andExpect(jsonPath("$.codigo").value(texto(creado, "codigo"))).andReturn();
        assertNotEquals(creado.getResponse().getHeader("ETag"), actualizado.getResponse().getHeader("ETag"));
        solicitar(put("/api/pacientes/" + id).session(sesion).header("If-Match", creado.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content(datosPaciente("12345678", true))).andExpect(status().isConflict());
        solicitar(put("/api/pacientes/" + id).session(sesion).contentType(MediaType.APPLICATION_JSON).content(datosPaciente("12345678", true)))
                .andExpect(status().isPreconditionRequired());
        solicitar(delete("/api/pacientes/" + id).session(sesion)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void doctorBuscaPorDniSinAccesoCompletoHastaVincularUnaCita() throws Exception {
        var creado = paciente(sesion, "12345678");
        String id = texto(creado, "id");
        solicitar(get("/api/pacientes/buscar-dni?dni=12345678").session(doctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].puedeConsultar").value(false)).andExpect(jsonPath("$[0].fechaNacimiento").doesNotExist())
                .andExpect(jsonPath("$[0].correo").doesNotExist()).andExpect(jsonPath("$[0].telefono").doesNotExist());
        solicitar(get("/api/pacientes/" + id).session(doctor)).andExpect(status().isNotFound());
        solicitar(get("/api/pacientes").session(doctor)).andExpect(jsonPath("$.total").value(0));
        var turno = turno(profesional, "08:00", "12:00"); aprobar(turno);
        reservar(doctor, creado, turno, "08:10", "08:45").andExpect(status().isCreated());
        solicitar(get("/api/pacientes/" + id).session(doctor)).andExpect(status().isOk());
        solicitar(get("/api/pacientes").session(doctor)).andExpect(jsonPath("$.total").value(1));
        solicitar(get("/api/pacientes/" + id).session(segundoDoctor)).andExpect(status().isNotFound());
        solicitar(put("/api/pacientes/" + id).session(segundoDoctor).header("If-Match", creado.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content(datosPaciente("12345678", false))).andExpect(status().isNotFound());
    }

    @Test
    void reasignarUnaCitaNoDejaAccesoResidualAlDoctorAnterior() throws Exception {
        var paciente = paciente(sesion, "12345678");
        var turno = turno(profesional, "08:00", "12:00");
        var segundo = turno(segundoProfesional, "08:00", "12:00"); aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "08:00", "08:30").andExpect(status().isCreated()).andReturn();
        solicitar(put("/api/citas/" + texto(cita, "id")).session(sesion).header("If-Match", cita.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content(datosCita(paciente, segundo, "09:00", "09:30"))).andExpect(status().isOk());
        solicitar(get("/api/pacientes/" + texto(paciente, "id")).session(doctor)).andExpect(status().isNotFound());
        solicitar(get("/api/pacientes/" + texto(paciente, "id")).session(segundoDoctor)).andExpect(status().isOk());
    }

    @Test
    void registraElDoctorSinDependerDeAutoprogramacionYRevalidaSuRol() throws Exception {
        var creado = paciente(doctor, "12345678");
        solicitar(get("/api/pacientes/" + texto(creado, "id")).session(doctor)).andExpect(status().isOk());
        consultas.update("DELETE FROM user_role WHERE id_user_account=? AND id_role=100", doctor.getAttribute("medishift.idCuenta"));
        solicitar(get("/api/pacientes").session(doctor)).andExpect(status().isForbidden());
        solicitar(get("/api/pacientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void aislaInstitucionesYPermiteElMismoDniEnOtra() throws Exception {
        var creado = paciente(sesion, "12345678");
        UUID otraInstitucion = nuevaInstitucion(); UUID otraCuenta = nuevaCuenta(otraInstitucion);
        inicializar(otraInstitucion, otraCuenta);
        var otraSesion = sesionDe(otraInstitucion, otraCuenta);
        solicitar(get("/api/pacientes/" + texto(creado, "id")).session(otraSesion)).andExpect(status().isNotFound());
        solicitar(get("/api/pacientes/buscar-dni?dni=12345678").session(otraSesion)).andExpect(jsonPath("$.length()").value(0));
        paciente(otraSesion, "12345678");
    }

    @Test
    void validaDniNacimientoCamposAjenosYNoDuplicaAuditoriaConDatosPersonales() throws Exception {
        for (String datos : new String[]{datosPaciente("123", true), datosPaciente("12345678", true).replace("2000-02-29", "9999-01-01"),
                datosPaciente("12345678", true).replace("\"activo\":true", "\"activo\":true,\"diagnostico\":\"No solicitado\""),
                datosPaciente("12345678", true).replace("2000-02-29", "2001-02-29")}) {
            int estado = solicitar(post("/api/pacientes").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datos)).andReturn().getResponse().getStatus();
            assertTrue(estado == 400 || estado == 409);
        }
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM paciente", Integer.class));
        paciente(sesion, "12345678");
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM audit_event WHERE entity_name_audit_event='paciente' AND (after_audit_event ? 'dni' OR after_audit_event ? 'correo' OR after_audit_event ? 'nombres')", Integer.class));
        solicitar(get("/api/pacientes?pagina=-1").session(sesion)).andExpect(status().isBadRequest());
        solicitar(get("/api/pacientes/buscar-dni?dni=123").session(sesion)).andExpect(status().isBadRequest());
        solicitar(get("/api/pacientes").session(sesion)).andExpect(header().string("Cache-Control", "no-store"));
    }
}
