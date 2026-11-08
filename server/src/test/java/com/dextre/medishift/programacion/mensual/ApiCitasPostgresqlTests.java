package com.dextre.medishift.programacion.mensual;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiCitasPostgresqlTests extends SoporteAtencionPostgresql {
    @Test
    void exigeAprobacionDuracionValidaPacienteActivoYPermisosPorDoctor() throws Exception {
        var paciente = paciente(sesion, "12345678");
        var turno = turno(profesional, "08:00", "12:00");
        reservar(doctor, paciente, turno, "08:00", "08:35").andExpect(status().isConflict());
        aprobar(turno);
        reservar(segundoDoctor, paciente, turno, "08:00", "08:35").andExpect(status().isNotFound());
        for (String[] horas : List.of(new String[]{"07:59","08:30"}, new String[]{"11:50","12:01"})) {
            reservar(doctor, paciente, turno, horas[0], horas[1]).andExpect(status().isConflict());
        }
        reservar(doctor, paciente, turno, "08:35", "08:35").andExpect(status().isBadRequest());
        consultas.update("UPDATE paciente SET activo=false WHERE id=?::uuid", texto(paciente, "id"));
        reservar(doctor, paciente, turno, "08:00", "08:35").andExpect(status().isConflict());
        consultas.update("UPDATE paciente SET activo=true WHERE id=?::uuid", texto(paciente, "id"));
        var cita = reservar(doctor, paciente, turno, "08:00", "08:35").andExpect(status().isCreated()).andReturn();
        solicitar(get("/api/citas/" + texto(cita, "id")).session(segundoDoctor)).andExpect(status().isNotFound());
        solicitar(get("/api/citas?fecha=2032-11-02").session(segundoDoctor)).andExpect(jsonPath("$.citas.length()").value(0));
        estado(segundoDoctor, cita, "cancelada").andExpect(status().isNotFound());
        estado(doctor, cita, "atendida").andExpect(status().isConflict());
        estado(doctor, cita, "no_asistio").andExpect(status().isConflict());
    }

    @Test
    void impideCrucesDeMedicoYPacienteAdmiteContiguasYLiberaAlCancelar() throws Exception {
        var paciente = paciente(sesion, "12345678"); var otro = paciente(sesion, "87654321");
        var turno = turno(profesional, "08:00", "12:00");
        var segundo = turno(segundoProfesional, "08:00", "12:00"); aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "08:00", "09:00").andExpect(status().isCreated()).andReturn();
        reservar(doctor, otro, turno, "08:30", "09:30").andExpect(status().isConflict());
        reservar(segundoDoctor, paciente, segundo, "08:30", "09:30").andExpect(status().isConflict());
        reservar(segundoDoctor, paciente, segundo, "09:00", "09:30").andExpect(status().isCreated());
        estado(doctor, cita, "cancelada").andExpect(status().isOk());
        reservar(doctor, otro, turno, "08:00", "09:00").andExpect(status().isCreated());
        estado(doctor, cita, "cancelada").andExpect(status().isConflict());
        solicitar(get("/api/pacientes/" + texto(paciente, "id") + "/citas").session(doctor)).andExpect(jsonPath("$.total").value(1));
        solicitar(get("/api/pacientes/" + texto(paciente, "id") + "/citas").session(sesion)).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void reprogramaConRevisionSinCambiarPacienteYNoModificaEstadosFinales() throws Exception {
        var paciente = paciente(sesion, "12345678"); var otro = paciente(sesion, "87654321");
        var turno = turno(profesional, "08:00", "12:00"); aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "08:00", "08:35").andExpect(status().isCreated()).andReturn();
        String ruta = "/api/citas/" + texto(cita, "id");
        solicitar(put(ruta).session(doctor).header("If-Match", cita.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                .content(datosCita(otro, turno, "09:00", "09:20"))).andExpect(status().isConflict());
        var nueva = solicitar(put(ruta).session(doctor).header("If-Match", cita.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                .content(datosCita(paciente, turno, "09:00", "09:20"))).andExpect(status().isOk()).andReturn();
        solicitar(put(ruta).session(doctor).header("If-Match", cita.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                .content(datosCita(paciente, turno, "10:00", "10:20"))).andExpect(status().isConflict());
        estado(doctor, nueva, "cancelada").andExpect(status().isOk());
        var finalizada = solicitar(get(ruta).session(doctor)).andReturn();
        solicitar(put(ruta).session(doctor).header("If-Match", finalizada.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                .content(datosCita(paciente, turno, "10:00", "10:20"))).andExpect(status().isConflict());
    }

    @Test
    void dosReservasSimultaneasDelMismoIntervaloConfirmanUnaSolaCita() throws Exception {
        var paciente = paciente(sesion, "12345678"); var otro = paciente(sesion, "87654321");
        var turno = turno(profesional, "08:00", "12:00"); aprobar(turno);
        CountDownLatch salida = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> concurrente(paciente, turno, salida));
            var segunda = ejecutor.submit(() -> concurrente(otro, turno, salida));
            salida.countDown();
            assertEquals(List.of(201,409), List.of(primera.get(20,TimeUnit.SECONDS), segunda.get(20,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1, consultas.queryForObject("SELECT count(*) FROM cita", Integer.class));
    }

    private int concurrente(MvcResult paciente, MvcResult turno, CountDownLatch salida) throws Exception {
        assertTrue(salida.await(5, TimeUnit.SECONDS));
        return reservar(doctor, paciente, turno, "08:00", "09:00").andReturn().getResponse().getStatus();
    }

    @Test
    void falloDeAuditoriaRevierteCitaYVinculacion() throws Exception {
        var paciente = paciente(sesion, "12345678"); var turno = turno(profesional, "08:00", "12:00"); aprobar(turno);
        fallarAuditoria("create");
        reservar(doctor, paciente, turno, "08:00", "09:00").andExpect(status().isInternalServerError());
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM cita", Integer.class));
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM paciente_profesional", Integer.class));
    }
}
