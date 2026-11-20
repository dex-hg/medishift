package com.dextre.medishift.programacion.mensual;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-atencion", matches = "true")
class ApiEtiquetasSubareasPostgresqlTests extends SoporteAtencionPostgresql {
    private MvcResult etiquetaCirugia;
    private UUID cirugia;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        etiquetaCirugia = json(post("/api/etiquetas"), "{\"nombre\":\"Cirugía plástica\"}")
                .andExpect(status().isCreated()).andReturn();
        cirugia = identificador(etiquetaCirugia);
    }

    @Test
    void mueveUnMedicoConDosEtiquetasYCompruebaLaRestriccionDelConsultorio() throws Exception {
        var medica = json(post("/api/profesionales"), ficha(especialidad, cirugia))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etiquetas.length()").value(2)).andReturn();
        UUID persona = identificador(medica);
        UUID evaluacion = crearSubarea("PRE", especialidad, cirugia);
        UUID procedimientos = crearSubarea("PROC", cirugia);
        UUID salaRestringida = crearSala("PRE-02", evaluacion, cirugia);
        UUID salaGeneral = crearSala("PROC-01", procedimientos);
        var turno = json(post("/api/planificador/turnos"), asignacion(area, persona, consultorio, especialidad, "10:00"))
                .andExpect(status().isCreated()).andReturn();
        String ruta = "/api/planificador/turnos/" + identificador(turno);
        json(put(ruta).header("If-Match", etag(turno)), asignacion(evaluacion, persona, salaRestringida, especialidad, "10:00"))
                .andExpect(status().isConflict());
        assertEquals(0, numero("SELECT count(*) FROM schedule WHERE id_planning_unit=?", evaluacion));
        var movido = json(put(ruta).header("If-Match", etag(turno)), asignacion(evaluacion, persona, salaRestringida, cirugia, "10:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(texto(turno, "id")))
                .andExpect(jsonPath("$.idEtiqueta").value(cirugia.toString())).andReturn();
        assertNotEquals(texto(turno, "idPlan"), texto(movido, "idPlan"));
        json(post("/api/planificador/turnos"), asignacion(procedimientos, persona, salaGeneral, cirugia, "10:00"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idProfesional").exists());
        json(post("/api/planificador/turnos"), asignacion(procedimientos, persona, salaGeneral, cirugia, "12:00"))
                .andExpect(status().isCreated());
        assertEquals(2, numero("SELECT count(*) FROM schedule WHERE id_planning_unit IN (?,?)", evaluacion, procedimientos));
    }

    @Test
    void bastaUnaEtiquetaYLaSubareaConservaSusEtiquetasCuandoCambiaElPadre() throws Exception {
        UUID subarea = crearSubarea("PRE", especialidad, cirugia);
        UUID sala = crearSala("PRE-01", subarea);
        solicitar(get("/api/consultorios/" + sala).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.area.id").value(subarea.toString()))
                .andExpect(jsonPath("$.area.idPadre").value(area.toString()))
                .andExpect(jsonPath("$.area.etiquetas.length()").value(2))
                .andExpect(jsonPath("$.etiquetas.length()").value(0));
        json(post("/api/planificador/turnos"), asignacion(subarea, profesional, sala, especialidad, "10:00"))
                .andExpect(status().isCreated());
        UUID otraEtiqueta = identificador(json(post("/api/etiquetas"), "{\"nombre\":\"Medicina interna\"}")
                .andExpect(status().isCreated()).andReturn());
        var padre = configuracion(area);
        json(put("/api/planificador/areas/" + area).header("If-Match", revision(padre)), datosArea("Padre", null, especialidad, otraEtiqueta))
                .andExpect(status().isOk());
        assertEquals(2, numero("SELECT count(*) FROM area_etiqueta WHERE id_area=?", subarea));
        UUID ajena = nuevaUnidad(nuevaInstitucion(), "AJENA");
        json(put("/api/planificador/areas/" + subarea).header("If-Match", revision(configuracion(subarea))),
                datosArea("Preoperatoria", ajena, especialidad, cirugia)).andExpect(status().isNotFound());
        assertThrows(DataAccessException.class, () -> consultas.update(
                "UPDATE planning_unit SET id_area_padre=? WHERE id_planning_unit=?", subarea, area));
        assertThrows(DataAccessException.class, () -> consultas.update(
                "UPDATE planning_unit SET id_area_padre=? WHERE id_planning_unit=?", ajena, subarea));
    }

    @Test
    void protegeVinculosVigentesYConservaLaEtiquetaOriginalAlRenombrar() throws Exception {
        var medica = json(post("/api/profesionales"), ficha(especialidad, cirugia)).andExpect(status().isCreated()).andReturn();
        UUID persona = identificador(medica);
        UUID subarea = crearSubarea("PRE", especialidad, cirugia);
        UUID sala = crearSala("PRE-01", subarea);
        var turno = json(post("/api/planificador/turnos"), asignacion(subarea, persona, sala, cirugia, "10:00"))
                .andExpect(status().isCreated()).andReturn();
        json(put("/api/profesionales/" + persona).header("If-Match", revision(medica)), ficha(especialidad))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.idsEtiquetas").exists());
        json(put("/api/etiquetas/" + cirugia).header("If-Match", etag(etiquetaCirugia)), "{\"nombre\":\"Cirugía plástica\",\"activa\":false}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.activa").exists());
        json(put("/api/etiquetas/" + cirugia).header("If-Match", etag(etiquetaCirugia)), "{\"nombre\":\"Cirugía reconstructiva\",\"activa\":true}")
                .andExpect(status().isOk());
        assertEquals("Cirugía plástica", consultas.queryForObject("SELECT nombre_etiqueta FROM shift WHERE id_shift=?", String.class, identificador(turno)));
        assertEquals(2, numero("SELECT count(*) FROM professional_specialty WHERE id_professional=? AND activo", persona));
        solicitar(get("/api/etiquetas").session(doctor)).andExpect(status().isForbidden());
        json(put("/api/profesionales/" + persona), ficha(especialidad, cirugia)).andExpect(status().isPreconditionRequired());
    }

    @Test
    void protegeLaCitaYEnsenaLaEtiquetaEnLaVersionArchivada() throws Exception {
        UUID subarea = crearSubarea("PRE", especialidad, cirugia);
        UUID sala = crearSala("PRE-01", subarea);
        var turno = turno(profesional, "08:00", "10:00");
        var aprobado = aprobar(turno);
        var ficha = paciente(sesion, "00090101");
        var cita = reservar(sesion, ficha, turno, "08:30", "09:00").andExpect(status().isCreated()).andReturn();
        String plan = texto(turno, "idPlan");
        solicitar(post("/api/planes-mensuales/" + plan + "/reabrir").session(sesion).header("If-Match", etag(aprobado)))
                .andExpect(status().isOk());
        var actual = turnoCalendario(identificador(turno));
        json(put("/api/planificador/turnos/" + identificador(turno)).header("If-Match", revision(actual)),
                asignacion(subarea, profesional, sala, especialidad, "08:00"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores.citas").exists());
        assertEquals(0, numero("SELECT count(*) FROM schedule WHERE id_planning_unit=?", subarea));
        MvcResult historial = solicitar(get("/api/planes-mensuales/" + plan + "/historial").session(sesion))
                .andExpect(status().isOk()).andReturn();
        String contenido = com.jayway.jsonpath.JsonPath.read(historial.getResponse().getContentAsString(), "$[0].contenido");
        assertEquals("Especialidad de ensayo", com.jayway.jsonpath.JsonPath.read(contenido, "$.turnos[0].nombre_etiqueta"));
        solicitar(get("/api/citas/" + identificador(cita)).session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idTurno").value(texto(turno, "id")));
    }

    @Test
    void quitarEtiquetaYTrasladarTurnoConcurrentesNoDejanUnaAsignacionInvalida() throws Exception {
        UUID subarea = crearSubarea("PRE", especialidad, cirugia);
        UUID sala = crearSala("PRE-01", subarea);
        var turno = turno(profesional, "08:00", "10:00");
        String revisionSubarea = revision(configuracion(subarea));
        CountDownLatch salir = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var mover = ejecutor.submit(() -> {
                salir.await(10, TimeUnit.SECONDS);
                return json(put("/api/planificador/turnos/" + identificador(turno)).header("If-Match", etag(turno)),
                        asignacion(subarea, profesional, sala, especialidad, "08:00")).andReturn().getResponse().getStatus();
            });
            var quitar = ejecutor.submit(() -> {
                salir.await(10, TimeUnit.SECONDS);
                return json(put("/api/planificador/areas/" + subarea).header("If-Match", revisionSubarea), datosArea("Preoperatoria", area, cirugia))
                        .andReturn().getResponse().getStatus();
            });
            salir.countDown();
            assertEquals(List.of(200, 409), List.of(mover.get(15, TimeUnit.SECONDS), quitar.get(15, TimeUnit.SECONDS))
                    .stream().sorted().toList());
        }
        assertEquals(0, numero("""
                SELECT count(*) FROM shift t JOIN schedule h USING (id_schedule)
                WHERE h.id_planning_unit=? AND NOT EXISTS (
                    SELECT 1 FROM area_etiqueta a WHERE a.id_area=h.id_planning_unit AND a.id_etiqueta=t.id_specialty)
                """, subarea));
    }

    private UUID crearSubarea(String codigo, UUID... etiquetas) throws Exception {
        return identificador(json(post("/api/planificador/areas"), """
                {"codigo":"%s","nombre":"%s","idAreaPadre":"%s","idsEtiquetas":%s}
                """.formatted(codigo, codigo, area, lista(etiquetas))).andExpect(status().isCreated()).andReturn());
    }

    private UUID crearSala(String codigo, UUID unidad, UUID... etiquetas) throws Exception {
        return identificador(json(post("/api/planificador/consultorios"), """
                {"idArea":"%s","codigo":"%s","nombre":"Sala %s","ubicacion":"Piso 2","idsEtiquetas":%s}
                """.formatted(unidad, codigo, codigo, lista(etiquetas))).andExpect(status().isCreated()).andReturn());
    }

    private String asignacion(UUID unidad, UUID persona, UUID sala, UUID etiqueta, String inicio) {
        return """
                {"idArea":"%s","idProfesional":"%s","idConsultorio":"%s","idEtiqueta":"%s",
                 "fecha":"2032-11-02","horaInicio":"%s","horaFin":"%02d:00","observacion":""}
                """.formatted(unidad, persona, sala, etiqueta, inicio, Integer.parseInt(inicio.substring(0, 2)) + 2);
    }

    private String ficha(UUID... etiquetas) {
        return """
                {"nombres":"Lucía","apellidos":"Vega","colegiatura":"CMP-ETQ","correo":"etiquetas@example.test",
                 "categoria":"Médica","estado":"Activo","idsEtiquetas":%s}
                """.formatted(lista(etiquetas));
    }

    private String datosArea(String nombre, UUID padre, UUID... etiquetas) {
        return """
                {"nombre":"%s","idAreaPadre":%s,"idsEtiquetas":%s,"estado":"Activo"}
                """.formatted(nombre, padre == null ? "null" : "\"" + padre + "\"", lista(etiquetas));
    }

    private String lista(UUID... etiquetas) {
        return Arrays.stream(etiquetas).map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private ResultActions json(MockHttpServletRequestBuilder solicitud, String datos) throws Exception {
        return solicitar(solicitud.session(sesionDe(institucion, actor)).contentType(MediaType.APPLICATION_JSON).content(datos));
    }

    private MvcResult calendario() throws Exception {
        return solicitar(get("/api/planificador?periodo=2032-11").session(sesion)).andExpect(status().isOk()).andReturn();
    }

    private String configuracion(UUID id) throws Exception {
        return revisionPorId(calendario(), "areas", id);
    }

    private String turnoCalendario(UUID id) throws Exception {
        return revisionPorId(calendario(), "turnos", id);
    }

    private String revisionPorId(MvcResult respuesta, String campo, UUID id) throws Exception {
        List<String> revisiones = com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(),
                "$." + campo + "[?(@.id == '" + id + "')].revision");
        return revisiones.getFirst();
    }

    private int numero(String consulta, Object... argumentos) { return consultas.queryForObject(consulta, Integer.class, argumentos); }
    private UUID identificador(MvcResult respuesta) throws Exception { return UUID.fromString(texto(respuesta, "id")); }
    private String etag(MvcResult respuesta) { return respuesta.getResponse().getHeader("ETag"); }
    private String revision(MvcResult respuesta) throws Exception { return revision(texto(respuesta, "revision")); }
    private String revision(String valor) { return "\"" + valor + "\""; }
}
