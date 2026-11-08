package com.dextre.medishift.programacion.mensual;

import java.util.UUID;
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
class ApiAprobacionCitasPostgresqlTests extends SoporteAtencionPostgresql {
    @Test
    void soloAdminApruebaRevisionActualQueIncluyeLosTurnos() throws Exception {
        var turno = turno(profesional, "08:00", "10:00");
        String plan = texto(turno, "idPlan");
        var anterior = solicitar(get("/api/planes-mensuales/" + plan).session(sesion)).andReturn();
        turno(segundoProfesional, "10:00", "12:00");
        solicitar(post("/api/planes-mensuales/" + plan + "/aprobar").session(sesion).header("If-Match", anterior.getResponse().getHeader("ETag")))
                .andExpect(status().isConflict());
        var actual = solicitar(get("/api/planes-mensuales/" + plan).session(sesion)).andReturn();
        solicitar(post("/api/planes-mensuales/" + plan + "/aprobar").session(doctor).header("If-Match", actual.getResponse().getHeader("ETag")))
                .andExpect(status().isForbidden());
        aprobar(turno);
        assertEquals(2, consultas.queryForObject("SELECT count(*) FROM shift WHERE id_schedule=?::uuid AND status_shift='approved'", Integer.class, plan));
    }

    @Test
    void reabreConCopiaHistoricaConservaCitasYBloqueaCambiosQueLasAfectan() throws Exception {
        var paciente = paciente(sesion, "12345678"); var turno = turno(profesional, "08:00", "12:00");
        var aprobado = aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "10:00", "11:00").andExpect(status().isCreated()).andReturn();
        var reabierto = reabrir(aprobado);
        String plan = texto(turno, "idPlan"); String ruta = "/api/planificador/turnos/" + texto(turno, "id");
        assertEquals(2, consultas.queryForObject("SELECT version_schedule FROM schedule WHERE id_schedule=?::uuid", Integer.class, plan));
        solicitar(get("/api/planes-mensuales/" + plan + "/historial").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1));
        solicitar(get("/api/citas/" + texto(cita, "id")).session(doctor)).andExpect(jsonPath("$.idTurno").value(texto(turno, "id")));
        reservar(doctor, paciente, turno, "11:00", "11:20").andExpect(status().isConflict());
        String revision = "\"" + revisionTurno(turno) + "\"";
        solicitar(put(ruta).session(sesion).header("If-Match", revision).contentType(MediaType.APPLICATION_JSON).content(datosTurno(profesional, "08:00", "10:30")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.citas").exists());
        solicitar(delete(ruta).session(sesion).header("If-Match", revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.errores.citas").exists());
        solicitar(put(ruta).session(sesion).header("If-Match", revision).contentType(MediaType.APPLICATION_JSON).content(datosTurno(profesional, "07:30", "12:30")))
                .andExpect(status().isOk());
        aprobar(turno);
        solicitar(get("/api/citas/" + texto(cita, "id")).session(doctor)).andExpect(jsonPath("$.estado").value("programada"));
        assertNotEquals(aprobado.getResponse().getHeader("ETag"), reabierto.getResponse().getHeader("ETag"));
    }

    @Test
    void citaCanceladaPermiteRetirarTurnoConservandoHistorial() throws Exception {
        var paciente = paciente(sesion, "12345678"); var turno = turno(profesional, "08:00", "12:00");
        var aprobado = aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "08:00", "09:00").andExpect(status().isCreated()).andReturn();
        estado(doctor, cita, "cancelada").andExpect(status().isOk());
        reabrir(aprobado);
        solicitar(delete("/api/planificador/turnos/" + texto(turno, "id")).session(sesion).header("If-Match", "\"" + revisionTurno(turno) + "\""))
                .andExpect(status().isNoContent());
        assertEquals("cancelled", consultas.queryForObject("SELECT status_shift FROM shift WHERE id_shift=?::uuid", String.class, texto(turno, "id")));
        solicitar(get("/api/pacientes/" + texto(paciente, "id") + "/citas").session(sesion)).andExpect(jsonPath("$.citas[0].estado").value("cancelada"));
    }

    @Test
    void baseDeDatosImpideCruceDirectoYRetiroDePlanConCitas() throws Exception {
        var paciente = paciente(sesion, "12345678"); var turno = turno(profesional, "08:00", "12:00"); aprobar(turno);
        var cita = reservar(doctor, paciente, turno, "08:00", "09:00").andExpect(status().isCreated()).andReturn();
        assertThrows(org.springframework.dao.DataAccessException.class, () -> consultas.update("""
                INSERT INTO cita(id,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador)
                SELECT ?,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador FROM cita WHERE id=?::uuid
                """, UUID.randomUUID(), texto(cita, "id")));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> consultas.update("UPDATE schedule SET status_schedule='cancelled' WHERE id_schedule=?::uuid", texto(turno, "idPlan")));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> consultas.update("UPDATE shift SET end_at_shift='2032-11-02T08:30:00-05:00' WHERE id_shift=?::uuid", texto(turno, "id")));
        assertEquals(1, consultas.queryForObject("SELECT count(*) FROM cita", Integer.class));
    }

    private MvcResult reabrir(MvcResult aprobado) throws Exception {
        return solicitar(post("/api/planes-mensuales/" + texto(aprobado, "id") + "/reabrir").session(sesion).header("If-Match", aprobado.getResponse().getHeader("ETag")))
                .andExpect(status().isOk()).andReturn();
    }

    private String revisionTurno(MvcResult turno) throws Exception {
        String id = texto(turno, "id");
        var calendario = solicitar(get("/api/planificador?periodo=2032-11").session(sesion)).andExpect(status().isOk()).andReturn();
        java.util.List<java.util.Map<String,Object>> turnos = com.jayway.jsonpath.JsonPath.read(calendario.getResponse().getContentAsString(), "$.turnos");
        var actual = turnos.stream().filter(registro -> id.equals(registro.get("id"))).findFirst().orElseThrow();
        return (String) actual.get("revision");
    }
}
