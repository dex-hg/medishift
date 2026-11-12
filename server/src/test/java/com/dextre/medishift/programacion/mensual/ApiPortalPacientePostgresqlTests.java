package com.dextre.medishift.programacion.mensual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiPortalPacientePostgresqlTests extends SoporteAtencionPostgresql {
    private static final String PORTAL = "/api/portal-paciente";
    private static final String DNI = "00345678";
    private static final String NACIMIENTO = "2000-02-29";

    @Test
    void ingresaConDniYFechaYSeparaLaSesionDelPersonal() throws Exception {
        paciente(sesion, DNI);
        solicitar(ingreso(DNI, NACIMIENTO).header("Origin", "https://externo.example")).andExpect(status().isForbidden());
        calendario(sesion, "2032-11").andExpect(status().isUnauthorized());
        var pacienteAutenticado = sesionPaciente(solicitar(ingreso(DNI, NACIMIENTO))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("")).andReturn());

        for (String operativa : List.of("/api/sesion", "/api/citas/calendario?periodo=2032-11",
                "/api/configuracion", "/api/pacientes")) {
            solicitar(get(operativa).session(pacienteAutenticado)).andExpect(status().isUnauthorized());
        }
        solicitar(post("/api/citas").session(pacienteAutenticado).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        calendario(pacienteAutenticado, "2032-11").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.hospitales.length()").value(1))
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.fechaNacimiento").doesNotExist());
        solicitar(delete(PORTAL + "/sesion").session(pacienteAutenticado)).andExpect(status().isNoContent());
        assertTrue(pacienteAutenticado.isInvalid());
        solicitar(get(PORTAL + "/calendario?periodo=2032-11")).andExpect(status().isUnauthorized());
    }

    @Test
    void validaLosDosCamposYUsaElMismoErrorParaUnaCoincidenciaInvalida() throws Exception {
        paciente(sesion, DNI);
        solicitar(ingreso(DNI, "2001-02-29")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento").exists());
        solicitar(ingreso(DNI, "2999-01-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento").exists());
        solicitar(post(PORTAL + "/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":345678,\"fechaNacimiento\":\"2000-02-29\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.dni").exists());
        solicitar(post(PORTAL + "/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"00345678\",\"contrasena\":\"clave antigua\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.fechaNacimiento").exists());
        var fechaDistinta = solicitar(ingreso(DNI, "2000-02-28")).andExpect(status().isUnauthorized()).andReturn();
        var dniDistinto = solicitar(ingreso("10345678", NACIMIENTO)).andExpect(status().isUnauthorized()).andReturn();
        assertEquals(texto(fechaDistinta, "mensaje"), texto(dniDistinto, "mensaje"));
        consultas.update("UPDATE paciente SET activo=false WHERE dni=?", DNI);
        solicitar(ingreso(DNI, NACIMIENTO)).andExpect(status().isUnauthorized());
    }

    @Test
    void revalidaDniYFechaEnCadaFichaYUsaElMesLocalDeCadaHospital() throws Exception {
        var ficha = paciente(sesion, DNI);
        var ajena = paciente(sesion, "12345679");
        var turnoLocal = turno(profesional, "08:00", "12:00");
        aprobar(turnoLocal);
        var citaLocal = reservar(doctor, ficha, turnoLocal, "08:00", "08:30").andExpect(status().isCreated()).andReturn();
        reservar(doctor, ajena, turnoLocal, "09:00", "09:30").andExpect(status().isCreated());
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(doctor))
                .andExpect(jsonPath("$.citas[0].idArea").value(area.toString()))
                .andExpect(jsonPath("$.citas[0].area").value("Unidad de ensayo"))
                .andExpect(jsonPath("$.citas[0].dni").doesNotExist());
        solicitar(get("/api/citas/calendario?periodo=2032-11").session(segundoDoctor))
                .andExpect(jsonPath("$.citas.length()").value(0));

        UUID hospital = nuevaInstitucion();
        UUID cuenta = nuevaCuenta(hospital);
        inicializar(hospital, cuenta);
        consultas.update("UPDATE institution SET name_institution=?,time_zone_institution=? WHERE id_institution=?",
                "Segundo hospital", "America/Mexico_City", hospital);
        var segundaFicha = paciente(sesionDe(hospital, cuenta), DNI);
        UUID idSegundaFicha = UUID.fromString(texto(segundaFicha, "id"));
        UUID citaSegundoHospital = citaEnHospital(hospital, cuenta, idSegundaFicha, LocalDate.of(2032, 11, 3), "10:00:00");
        UUID citaFinDeOctubre = citaEnHospital(hospital, cuenta, idSegundaFicha, LocalDate.of(2032, 10, 31), "23:15:00");

        var acceso = sesionPaciente(solicitar(ingreso(DNI, NACIMIENTO)).andExpect(status().isNoContent()).andReturn());
        calendario(acceso, "2032-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitales.length()").value(2))
                .andExpect(jsonPath("$.hospitales[1].nombre").value("Segundo hospital"))
                .andExpect(jsonPath("$.citas.length()").value(2))
                .andExpect(jsonPath("$.citas[0].id").value(texto(citaLocal, "id")))
                .andExpect(jsonPath("$.citas[1].id").value(citaSegundoHospital.toString()))
                .andExpect(jsonPath("$.citas[1].ubicacion").value("Piso 2"))
                .andExpect(jsonPath("$.citas[1].profesional").value("Doctora Segunda"))
                .andExpect(jsonPath("$.citas[0].idPaciente").doesNotExist())
                .andExpect(jsonPath("$.turnos").doesNotExist());
        calendario(acceso, "2032-10").andExpect(status().isOk()).andExpect(jsonPath("$.citas.length()").value(1))
                .andExpect(jsonPath("$.citas[0].id").value(citaFinDeOctubre.toString()));
        consultas.update("UPDATE paciente SET fecha_nacimiento=DATE '2000-02-28' WHERE id=?", idSegundaFicha);
        calendario(acceso, "2032-11").andExpect(jsonPath("$.hospitales.length()").value(1))
                .andExpect(jsonPath("$.citas.length()").value(1));
        var accesoOtraFecha = sesionPaciente(solicitar(ingreso(DNI, "2000-02-28")).andExpect(status().isNoContent()).andReturn());
        calendario(accesoOtraFecha, "2032-11").andExpect(jsonPath("$.hospitales.length()").value(1))
                .andExpect(jsonPath("$.hospitales[0].nombre").value("Segundo hospital"))
                .andExpect(jsonPath("$.citas[0].id").value(citaSegundoHospital.toString()));
        consultas.update("UPDATE paciente SET activo=false WHERE id=?", idSegundaFicha);
        calendario(accesoOtraFecha, "2032-11").andExpect(status().isUnauthorized());
        assertTrue(accesoOtraFecha.isInvalid());
    }

    @Test
    void limitaLosIntentosFallidosDeUnMismoDocumento() throws Exception {
        String documento = "00456789";
        paciente(sesion, documento);
        for (int intento = 0; intento < 8; intento++) {
            solicitar(ingreso(documento, "2000-02-28")).andExpect(status().isUnauthorized());
        }
        solicitar(ingreso(documento, NACIMIENTO)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private UUID citaEnHospital(UUID hospital, UUID cuenta, UUID ficha, LocalDate dia, String hora) {
        UUID sala = UUID.randomUUID(), medico = UUID.randomUUID(), plan = UUID.randomUUID(), turno = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        String codigo = id.toString().substring(0, 8);
        consultas.update("INSERT INTO room(id_room,id_institution,code_room,name_room,location_room,is_general_room,status_room) "
                + "VALUES (?,?,?,'Sala segunda','Piso 2',true,'active')", sala, hospital, codigo);
        consultas.update("""
                INSERT INTO professional(id_professional,id_institution,first_name_professional,last_name_professional,
                    license_number_professional,category_professional,email_professional,status_professional)
                VALUES (?,?,'Doctora','Segunda',?,'Medico',?,'active')
                """, medico, hospital, codigo, codigo + "@medishift.test");
        consultas.update("INSERT INTO professional_specialty VALUES (?,?,true)", medico, especialidad);
        consultas.update("""
                INSERT INTO schedule(id_schedule,id_institution,period_start_schedule,period_end_schedule,version_schedule,
                    status_schedule,id_creator_user_account,id_approver_user_account,approved_at_schedule)
                VALUES (?,?,?,?,1,'approved',?,?,now())
                """, plan, hospital, dia.minusDays(dia.getDayOfWeek().getValue() - 1), dia, cuenta, cuenta);
        String local = dia + "T" + hora;
        consultas.update("""
                INSERT INTO shift(id_shift,id_institution,id_schedule,id_professional,id_room,id_specialty,
                    start_at_shift,end_at_shift,status_shift)
                VALUES (?,?,?,?,?,?,?::timestamp AT TIME ZONE 'America/Mexico_City',
                    (?::timestamp + interval '40 minutes') AT TIME ZONE 'America/Mexico_City','approved')
                """, turno, hospital, plan, medico, sala, especialidad, local, local);
        consultas.update("""
                INSERT INTO cita(id,id_institucion,id_paciente,id_turno,id_profesional,id_consultorio,inicio,fin,id_creador)
                VALUES (?,?,?,?,?,?,?::timestamp AT TIME ZONE 'America/Mexico_City',
                    (?::timestamp + interval '20 minutes') AT TIME ZONE 'America/Mexico_City',?)
                """, id, hospital, ficha, turno, medico, sala, local, local, cuenta);
        return id;
    }

    private ResultActions calendario(MockHttpSession acceso, String periodo) throws Exception {
        return solicitar(get(PORTAL + "/calendario").session(acceso).param("periodo", periodo));
    }

    private MockHttpServletRequestBuilder ingreso(String dni, String nacimiento) {
        return post(PORTAL + "/sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"" + dni + "\",\"fechaNacimiento\":\"" + nacimiento + "\"}");
    }

    private MockHttpSession sesionPaciente(MvcResult respuesta) {
        return (MockHttpSession) respuesta.getRequest().getSession(false);
    }
}
