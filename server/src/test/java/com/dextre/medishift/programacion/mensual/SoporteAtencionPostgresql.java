package com.dextre.medishift.programacion.mensual;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class SoporteAtencionPostgresql extends SoporteApiPlanesMensuales {
    protected UUID area;
    protected UUID segundoProfesional;
    protected UUID segundoConsultorio;
    protected MockHttpSession doctor;
    protected MockHttpSession segundoDoctor;

    @BeforeEach
    void prepararAtencion() throws Exception {
        ejecutarArchivo("V004__configuracion_cuenta_institucion.sql");
        ejecutarArchivo("V005__autoprogramacion_medica.sql");
        ejecutarArchivo("V006__pacientes_citas.sql");
        inicializar(institucion, actor);
        area = nuevaUnidad(institucion, "ATENCION");
        consultas.update("UPDATE planning_unit SET id_specialty=? WHERE id_planning_unit=?", especialidad, area);
        consultas.update("UPDATE room SET id_planning_unit=? WHERE id_room=?", area, consultorio);
        consultas.update("INSERT INTO role VALUES (100,'doctor','Doctor de ensayo')");
        UUID cuenta = nuevaCuenta(institucion);
        UUID segundaCuenta = nuevaCuenta(institucion);
        consultas.update("INSERT INTO user_role VALUES (?,100),(?,100)", cuenta, segundaCuenta);
        consultas.update("UPDATE professional SET id_user_account=? WHERE id_professional=?", cuenta, profesional);
        segundoProfesional = UUID.randomUUID();
        segundoConsultorio = UUID.randomUUID();
        consultas.update("""
                INSERT INTO professional(id_professional,id_institution,id_user_account,first_name_professional,last_name_professional,
                license_number_professional,category_professional,email_professional,status_professional)
                VALUES (?,?,?,'Segundo','Doctor','P02','Medico','segundo@medishift.test','active')
                """, segundoProfesional, institucion, segundaCuenta);
        consultas.update("INSERT INTO professional_specialty VALUES (?,?,true)", segundoProfesional, especialidad);
        consultas.update("""
                INSERT INTO room(id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room,id_planning_unit)
                VALUES (?,?,'R02','Segunda sala','Piso 1',true,'active',?)
                """, segundoConsultorio, institucion, area);
        doctor = sesionDe(institucion, cuenta);
        segundoDoctor = sesionDe(institucion, segundaCuenta);
    }

    protected String datosPaciente(String dni, boolean activo) {
        return """
                {"nombres":"Ana María","apellidos":"Pérez Soto","dni":"%s","fechaNacimiento":"2000-02-29",
                "correo":"ana@medishift.test","telefono":"+51987654321","activo":%s}
                """.formatted(dni, activo);
    }

    protected MvcResult paciente(MockHttpSession acceso, String dni) throws Exception {
        return solicitar(post("/api/pacientes").session(acceso).contentType(MediaType.APPLICATION_JSON).content(datosPaciente(dni, true)))
                .andExpect(status().isCreated()).andReturn();
    }

    protected MvcResult turno(UUID persona, String desde, String hasta) throws Exception {
        return solicitar(post("/api/planificador/turnos").session(sesion).contentType(MediaType.APPLICATION_JSON).content(datosTurno(persona, desde, hasta)))
                .andExpect(status().isCreated()).andReturn();
    }

    protected String datosTurno(UUID persona, String desde, String hasta) {
        return """
                {"idArea":"%s","idProfesional":"%s","idConsultorio":"%s","idEspecialidad":"%s",
                "fecha":"2032-11-02","horaInicio":"%s","horaFin":"%s","observacion":""}
                """.formatted(area, persona, persona.equals(profesional) ? consultorio : segundoConsultorio, especialidad, desde, hasta);
    }

    protected MvcResult aprobar(MvcResult turno) throws Exception {
        String plan = texto(turno, "idPlan");
        MvcResult actual = solicitar(get("/api/planes-mensuales/" + plan).session(sesion)).andExpect(status().isOk()).andReturn();
        return solicitar(post("/api/planes-mensuales/" + plan + "/aprobar").session(sesion).header("If-Match", actual.getResponse().getHeader("ETag")))
                .andExpect(status().isOk()).andReturn();
    }

    protected String datosCita(MvcResult paciente, MvcResult turno, String desde, String hasta) throws Exception {
        return """
                {"idPaciente":"%s","idTurno":"%s","fecha":"2032-11-02","horaInicio":"%s","horaFin":"%s"}
                """.formatted(texto(paciente, "id"), texto(turno, "id"), desde, hasta);
    }

    protected ResultActions reservar(MockHttpSession acceso, MvcResult paciente, MvcResult turno, String desde, String hasta) throws Exception {
        return solicitar(post("/api/citas").session(acceso).contentType(MediaType.APPLICATION_JSON).content(datosCita(paciente, turno, desde, hasta)));
    }

    protected ResultActions estado(MockHttpSession acceso, MvcResult cita, String estado) throws Exception {
        return solicitar(put("/api/citas/" + texto(cita, "id") + "/estado").session(acceso)
                .header("If-Match", cita.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"" + estado + "\"}"));
    }

    protected String texto(MvcResult respuesta, String campo) throws Exception {
        return JsonPath.read(respuesta.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$." + campo);
    }
}
