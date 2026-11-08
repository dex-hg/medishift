package com.dextre.medishift.programacion.mensual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.dextre.medishift.atencion.ClienteIdentidadDni;
import com.dextre.medishift.atencion.DatosAtencion.IdentidadDni;
import com.dextre.medishift.catalogos.CatalogoException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiAgendaVisualPostgresqlTests extends SoporteAtencionPostgresql {
    @MockitoBean private ClienteIdentidadDni proveedor;

    @Test
    void consultaDniExigeSesionRolYContratoSinPersistirPaciente() throws Exception {
        when(proveedor.consultar("12345678")).thenReturn(new IdentidadDni("12345678", "Ana", "Paciente de prueba"));
        solicitar(post("/api/pacientes/consultar-dni").contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"12345678\"}"))
                .andExpect(status().isUnauthorized());
        solicitar(post("/api/pacientes/consultar-dni").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"12345678\",\"token\":\"rechazar\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proveedor);
        solicitar(post("/api/pacientes/consultar-dni").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"12345678\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.nombres").value("Ana")).andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.fechaNacimiento").doesNotExist());
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM paciente", Integer.class));
        consultas.update("DELETE FROM user_role WHERE id_user_account=? AND id_role=100", doctor.getAttribute("medishift.idCuenta"));
        solicitar(post("/api/pacientes/consultar-dni").session(doctor).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"12345678\"}"))
                .andExpect(status().isForbidden());
        verify(proveedor, times(1)).consultar("12345678");
    }

    @Test
    void permiteRegistroManualConNacimientoCuandoFallaLaConsulta() throws Exception {
        when(proveedor.consultar("12345678")).thenThrow(CatalogoException.noDisponible("Completa manualmente."));
        solicitar(post("/api/pacientes/consultar-dni").session(sesion).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"12345678\"}"))
                .andExpect(status().isServiceUnavailable());
        paciente(sesion, "12345678");
        solicitar(post("/api/pacientes").session(sesion).contentType(MediaType.APPLICATION_JSON)
                .content(datosPaciente("87654321", true).replace("\"fechaNacimiento\":\"2000-02-29\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calendarioMensualSoloIncluyeTurnosAprobadosYCitasAutorizadas() throws Exception {
        var paciente = paciente(sesion, "12345678");
        var propio = turno(profesional, "08:00", "12:00");
        turno(segundoProfesional, "08:00", "12:00");
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(1));
        aprobar(propio);
        reservar(doctor, paciente, propio, "08:00", "08:30").andExpect(status().isCreated());
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(3)).andExpect(jsonPath("$.citas.length()").value(1))
                .andExpect(jsonPath("$.periodo").value("2032-11"));
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(doctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(2)).andExpect(jsonPath("$.citas.length()").value(1));
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(segundoDoctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(1)).andExpect(jsonPath("$.citas.length()").value(0));
        solicitar(get("/api/citas/calendario?periodo=2032-12").session(sesion)).andExpect(jsonPath("$.turnos.length()").value(0));
        solicitar(get("/api/citas/calendario?periodo=0000-13").session(sesion)).andExpect(status().isBadRequest());
    }
}
