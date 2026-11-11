package com.dextre.medishift.programacion.mensual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiPortalPacientePostgresqlTests extends SoporteAtencionPostgresql {
    private static final String PORTAL = "/api/portal-paciente";
    private static final String DNI = "00345678";
    private static final String CONTRASENA = "Credencial de ensayo 2032";

    @BeforeEach
    void prepararPortal() throws Exception {
        ejecutarArchivo("V007__portal_pacientes.sql");
    }

    @Test
    void soloElAdministradorDeLaFichaEmiteYElPacienteActivaUnaVez() throws Exception {
        var ficha = paciente(sesion, DNI);
        String ruta = rutaAcceso(ficha);
        var ajena = nuevaInstitucion();
        var cuentaAjena = nuevaCuenta(ajena);
        inicializar(ajena, cuentaAjena);
        solicitar(post(ruta).session(sesionDe(ajena, cuentaAjena)).header("If-Match", revision(ficha)))
                .andExpect(status().isNotFound());
        solicitar(post(ruta).session(doctor).header("If-Match", revision(ficha))).andExpect(status().isForbidden());
        solicitar(post(ruta).session(sesion)).andExpect(status().isPreconditionRequired());
        solicitar(post(ruta).session(sesion).header("If-Match", "\"" + "0".repeat(64) + "\"")).andExpect(status().isConflict());
        solicitar(get(PORTAL + "/calendario?periodo=2032-11").session(sesion)).andExpect(status().isUnauthorized());
        String codigo = emitir(ficha);
        assertTrue(consultas.queryForObject("""
                SELECT length(hash_codigo)=64 AND hash_codigo<>? FROM codigo_activacion_paciente WHERE dni=?
                """, Boolean.class, codigo, DNI));
        solicitar(post(PORTAL + "/activacion").header("Origin", "https://externo.example")
                .contentType(MediaType.APPLICATION_JSON).content(datosIngreso(DNI, codigo))).andExpect(status().isForbidden());
        activar("10345678", codigo).andExpect(status().isUnauthorized());
        var pacienteAutenticado = sesionPaciente(activar(DNI, codigo).andExpect(status().isNoContent()).andReturn());
        assertTrue(consultas.queryForObject("""
                SELECT starts_with(hash_contrasena,'{pbkdf2@medishift-v1}') AND hash_contrasena<>?
                FROM cuenta_portal_paciente WHERE dni=?
                """, Boolean.class, CONTRASENA, DNI));
        activar(DNI, codigo).andExpect(status().isUnauthorized());
        solicitar(get(ruta).session(sesion)).andExpect(jsonPath("$.activado").value(true));
        solicitar(post(ruta).session(sesion).header("If-Match", revision(ficha))).andExpect(status().isConflict());

        for (String operativa : List.of("/api/sesion", "/api/citas/calendario?periodo=2032-11",
                "/api/configuracion", "/api/pacientes", ruta)) {
            solicitar(get(operativa).session(pacienteAutenticado)).andExpect(status().isUnauthorized());
        }
        solicitar(post("/api/citas").session(pacienteAutenticado).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        calendario(pacienteAutenticado, "2032-11").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        solicitar(delete(PORTAL + "/sesion").session(pacienteAutenticado)).andExpect(status().isNoContent());
        solicitar(get(PORTAL + "/calendario?periodo=2032-11")).andExpect(status().isUnauthorized());
        var ingreso = solicitar(post(PORTAL + "/sesion").contentType(MediaType.APPLICATION_JSON).content(datosIngreso(DNI, null)))
                .andExpect(status().isNoContent()).andReturn();
        calendario(sesionPaciente(ingreso), "2032-11").andExpect(status().isOk());
    }

    @Test
    void reuneSoloFichasActivasDelDniYUsaElMesLocalDeCadaHospital() throws Exception {
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

        String codigo = emitir(ficha);
        var acceso = sesionPaciente(activar(DNI, codigo).andExpect(status().isNoContent()).andReturn());
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
        consultas.update("UPDATE paciente SET activo=false WHERE id=?", idSegundaFicha);
        calendario(acceso, "2032-11").andExpect(jsonPath("$.hospitales.length()").value(1))
                .andExpect(jsonPath("$.citas.length()").value(1));
    }

    @Test
    void venceReemplazaYConsumeElCodigoEnUnaSolaTransaccion() throws Exception {
        var ficha = paciente(sesion, DNI);
        String anterior = emitir(ficha);
        String vigente = emitir(ficha);
        activar(DNI, anterior).andExpect(status().isUnauthorized());
        consultas.update("UPDATE codigo_activacion_paciente SET vence_en=now()-interval '1 second' WHERE dni=?", DNI);
        activar(DNI, vigente).andExpect(status().isUnauthorized());
        String nuevo = emitir(ficha);
        consultas.update("UPDATE paciente SET activo=false WHERE dni=?", DNI);
        activar(DNI, nuevo).andExpect(status().isUnauthorized());
        consultas.update("UPDATE paciente SET activo=true WHERE dni=?", DNI);
        CountDownLatch salida = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> activarEnParalelo(nuevo, salida));
            var segunda = ejecutor.submit(() -> activarEnParalelo(nuevo, salida));
            salida.countDown();
            assertEquals(List.of(204, 401), List.of(primera.get(20, TimeUnit.SECONDS),
                    segunda.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1, consultas.queryForObject("SELECT count(*) FROM cuenta_portal_paciente", Integer.class));
        assertEquals(0, consultas.queryForObject("SELECT count(*) FROM codigo_activacion_paciente", Integer.class));
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

    private String emitir(MvcResult ficha) throws Exception {
        return texto(solicitar(post(rutaAcceso(ficha)).session(sesion).header("If-Match", revision(ficha)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn(), "codigo");
    }

    private String rutaAcceso(MvcResult ficha) throws Exception {
        return "/api/pacientes/" + texto(ficha, "id") + "/acceso-portal";
    }

    private String revision(MvcResult ficha) {
        return ficha.getResponse().getHeader("ETag");
    }

    private ResultActions calendario(MockHttpSession acceso, String periodo) throws Exception {
        return solicitar(get(PORTAL + "/calendario").session(acceso).param("periodo", periodo));
    }

    private ResultActions activar(String dni, String codigo) throws Exception {
        return solicitar(post(PORTAL + "/activacion").contentType(MediaType.APPLICATION_JSON).content(datosIngreso(dni, codigo)));
    }

    private int activarEnParalelo(String codigo, CountDownLatch salida) throws Exception {
        assertTrue(salida.await(5, TimeUnit.SECONDS));
        return activar(DNI, codigo).andReturn().getResponse().getStatus();
    }

    private String datosIngreso(String dni, String codigo) {
        return "{\"dni\":\"" + dni + "\",\"contrasena\":\"" + CONTRASENA + "\""
                + (codigo == null ? "" : ",\"codigo\":\"" + codigo + "\"") + "}";
    }

    private MockHttpSession sesionPaciente(MvcResult respuesta) {
        return (MockHttpSession) respuesta.getRequest().getSession(false);
    }
}
