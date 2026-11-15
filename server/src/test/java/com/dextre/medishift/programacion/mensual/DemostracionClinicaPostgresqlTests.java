package com.dextre.medishift.programacion.mensual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class DemostracionClinicaPostgresqlTests extends SoporteApiPlanesMensuales {
    private static final List<String> SEDES = List.of("pe", "co");
    private static final Map<String, Integer> CATALOGOS = Map.of("professional", 32, "planning_unit", 9, "room", 16,
            "specialty", 8, "user_account", 2);
    private static final List<String> TABLAS = List.of("institution", "institution_settings", "role", "user_account",
            "user_role", "specialty", "planning_unit", "room", "room_specialty", "professional",
            "professional_specialty", "area_etiqueta", "schedule", "shift", "attendance", "paciente", "cita", "audit_event");
    private static final String ORIGEN = "http://127.0.0.1:5173";

    @Autowired private PasswordEncoder codificador;
    private final String claveEnsayo = UUID.randomUUID().toString();

    @BeforeEach
    void prepararAtencion() throws Exception {
        for (String archivo : List.of("V004__configuracion_cuenta_institucion.sql", "V005__autoprogramacion_medica.sql",
                "V006__pacientes_citas.sql", "V007__portal_pacientes.sql")) {
            ejecutarArchivo(archivo);
        }
    }

    @Test
    void creaLosCatalogosYPermiteUsarLaAgendaConLosTresTiposDeAcceso() throws Exception {
        cargar();
        assertEquals(3, numero("SELECT count(*) FROM institution"));
        assertEquals(0, numero("SELECT count(*) FROM user_role WHERE id_user_account=?", actor));
        for (String pais : SEDES) {
            UUID entidad = consultas.queryForObject("""
                    SELECT id_institution FROM institution WHERE code_institution=?
                    """, UUID.class, codigo(pais));
            for (var catalogo : CATALOGOS.entrySet()) {
                assertEquals(catalogo.getValue(), numero(
                        "SELECT count(*) FROM " + catalogo.getKey() + " WHERE id_institution=?", entidad));
            }
            assertEquals(240, numero("SELECT count(*) FROM paciente WHERE id_institucion=?", entidad));
            assertEquals(8, numero("SELECT count(*) FROM paciente WHERE id_institucion=? AND NOT activo", entidad));
            assertTrue(numero("SELECT count(*) FROM shift WHERE id_institution=?", entidad) > 1200);
            assertTrue(numero("SELECT count(*) FROM cita WHERE id_institucion=?", entidad) > 700);
            assertEquals(3, numero("""
                    SELECT count(*) FROM schedule WHERE id_institution=? AND status_schedule='draft'
                    """, entidad));
            assertEquals(2, numero("""
                    SELECT count(*) FROM area_etiqueta a JOIN planning_unit u ON u.id_planning_unit=a.id_area
                    WHERE a.id_institucion=? AND u.code_planning_unit='PRE' AND u.id_area_padre IS NOT NULL
                    """, entidad));
            assertEquals(2, numero("""
                    SELECT count(*) FROM (
                        SELECT id_professional FROM shift JOIN specialty USING (id_specialty)
                        WHERE shift.id_institution=? AND name_specialty IN ('Dermatología','Cirugía plástica')
                        GROUP BY id_professional HAVING count(DISTINCT id_specialty)=2
                    ) rotaciones
                    """, entidad));
            comprobarAgenda(pais, entidad);
        }
    }

    @Test
    void conservaLosCambiosEntreCargasYRevierteAlEncontrarUnaSedeAjena() throws Exception {
        UUID ajena = nuevaInstitucion();
        consultas.update("UPDATE institution SET code_institution=? WHERE id_institution=?", codigo("pe"), ajena);
        String antes = huella();
        assertThrows(DataAccessException.class, this::cargar);
        consultas.execute("ROLLBACK");
        assertEquals(antes, huella());

        consultas.update("UPDATE institution SET code_institution='AJENA' WHERE id_institution=?", ajena);
        cargar();
        assertEquals(1, consultas.update("""
                UPDATE shift SET note_shift='Editado en la demo'
                WHERE id_shift=(SELECT id_shift FROM shift WHERE status_shift='draft' LIMIT 1)
                """));
        assertEquals(1, consultas.update("""
                UPDATE user_account SET password_hash_user_account='hash-de-ensayo-cambiado'
                WHERE email_user_account='admin.co@medishift.example.test'
                """));
        assertEquals(1, consultas.update("""
                UPDATE cita SET estado='cancelada',version=version+1 WHERE id=(SELECT id FROM cita LIMIT 1)
                """));
        String editado = huella();
        cargar();
        assertEquals(editado, huella());
    }

    private void comprobarAgenda(String pais, UUID entidad) throws Exception {
        String periodo = consultas.queryForObject("""
                SELECT to_char(date_trunc('month',(now() AT TIME ZONE 'America/Lima')) + interval '1 month','YYYY-MM')
                """, String.class);
        MockHttpSession sesionAdministrador = iniciarSesion(pais, "admin", "esAdministrador");
        MockHttpSession sesionDoctora = iniciarSesion(pais, "doctor", "esDoctor");
        String idDoctora = consultas.queryForObject("""
                SELECT p.id_professional FROM professional p JOIN user_account u USING (id_institution,id_user_account)
                WHERE p.id_institution=? AND u.email_user_account=?
                """, UUID.class, entidad, "doctor." + pais + "@medishift.example.test").toString();
        solicitar(get("/api/planificador").param("periodo", periodo).session(sesionAdministrador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.areas.length()").value(9))
                .andExpect(jsonPath("$.turnos[0].id").exists());
        solicitar(get("/api/pacientes").session(sesionAdministrador)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(240));
        solicitar(get("/api/citas/calendario").param("periodo", periodo).session(sesionDoctora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.citas[0].idProfesional").value(idDoctora))
                .andExpect(jsonPath("$.citas[?(@.idProfesional != '%s')]", idDoctora).isEmpty());
        String dni = pais.equals("pe") ? "00090101" : "00091101";
        MockHttpSession portal = obtenerSesion(solicitar(post("/api/portal-paciente/sesion").header("Origin", ORIGEN)
                        .contentType("application/json").content("""
                                {"dni":"%s","fechaNacimiento":"1990-05-14"}
                                """.formatted(dni)))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false));
        solicitar(get("/api/portal-paciente/calendario").param("periodo", periodo).session(portal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hospitales.length()").value(1))
                .andExpect(jsonPath("$.hospitales[0].codigo").value(codigo(pais)))
                .andExpect(jsonPath("$.citas[0].codigoHospital").value(codigo(pais)));
    }

    private MockHttpSession iniciarSesion(String pais, String rol, String permiso) throws Exception {
        return obtenerSesion(solicitar(post("/api/sesion").header("Origin", ORIGEN)
                        .contentType("application/json").content("""
                                {"codigoInstitucion":"%s","correo":"%s.%s@medishift.example.test","contrasena":"%s"}
                                """.formatted(codigo(pais), rol, pais, claveEnsayo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$." + permiso).value(true))
                .andReturn().getRequest().getSession(false));
    }

    private MockHttpSession obtenerSesion(HttpSession sesion) {
        return assertInstanceOf(MockHttpSession.class, sesion);
    }

    private void cargar() throws Exception {
        assertTrue(esquema.matches("prueba_mensual_[0-9a-f]{32}"));
        String hash = codificador.encode(claveEnsayo);
        String claves = """
                {"pe":{"admin":"%1$s","doctor":"%1$s"},"co":{"admin":"%1$s","doctor":"%1$s"}}
                """.formatted(hash).strip();
        consultas.queryForObject("SELECT set_config('medishift.accesos_demostracion',?,false)", String.class, claves);
        String sql = Files.readString(Path.of("../db/datos_demostracion_clinica.sql"));
        String publico = "SET LOCAL search_path = public, pg_catalog;";
        assertTrue(sql.contains(publico));
        consultas.execute(sql.replace(publico, "SET LOCAL search_path = " + esquema + ", public, pg_catalog;"));
    }

    private int numero(String sql, Object... parametros) {
        return consultas.queryForObject(sql, Integer.class, parametros);
    }

    private String codigo(String pais) {
        return "DEMO-MEDISHIFT-" + pais.toUpperCase(java.util.Locale.ROOT);
    }

    private String huella() {
        return TABLAS.stream().map(tabla -> consultas.queryForObject(
                "SELECT md5(coalesce(string_agg(to_jsonb(fila)::text,'' ORDER BY to_jsonb(fila)::text),'')) FROM "
                        + tabla + " fila", String.class)).collect(Collectors.joining("\n"));
    }
}
