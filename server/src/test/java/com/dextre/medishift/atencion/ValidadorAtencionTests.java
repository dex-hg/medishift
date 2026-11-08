package com.dextre.medishift.atencion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.dextre.medishift.catalogos.CatalogoException;
import static org.junit.jupiter.api.Assertions.*;

class ValidadorAtencionTests {
    private Map<String,Object> paciente() {
        return new HashMap<>(Map.of("nombres", "  María   Ana ", "apellidos", "Pérez", "dni", "00123456", "fechaNacimiento", "2000-02-29",
                "correo", " ANA@EJEMPLO.PE ", "telefono", "+51987654321", "activo", true));
    }
    @Test
    void normalizaSinPerderCerosNiAceptarDatosClinicos() {
        var datos = paciente(); var resultado = ValidadorAtencion.paciente(datos);
        assertEquals("María Ana", resultado.nombres()); assertEquals("00123456", resultado.dni());
        assertEquals("ana@ejemplo.pe", resultado.correo());
        datos.put("diagnostico", "No solicitado");
        assertTrue(assertThrows(CatalogoException.class, () -> ValidadorAtencion.paciente(datos)).obtenerErrores().containsKey("diagnostico"));
    }
    @Test
    void rechazaNulosTiposUnicodeFechasImposiblesYDatosFueraDeLimite() {
        assertThrows(CatalogoException.class, () -> ValidadorAtencion.paciente(null));
        for (Map.Entry<String,Object> caso : Map.<String,Object>of("dni", 12345678, "nombres", "X\u0000", "apellidos", "\uD800",
                "fechaNacimiento", "2001-02-29", "telefono", "123", "correo", "sin-arroba", "activo", "true").entrySet()) {
            var datos = paciente(); datos.put(caso.getKey(), caso.getValue());
            assertTrue(assertThrows(CatalogoException.class, () -> ValidadorAtencion.paciente(datos)).obtenerErrores().containsKey(caso.getKey()));
        }
    }
    @Test
    void citaExigeIdentidadesHorasOrdenadasYCamposConocidos() {
        var datos = new HashMap<String,Object>(Map.of("idPaciente", UUID.randomUUID().toString(), "idTurno", UUID.randomUUID().toString(),
                "fecha", "2032-11-02", "horaInicio", "08:00", "horaFin", "08:17"));
        assertNotNull(ValidadorAtencion.cita(datos));
        datos.put("horaFin", "08:00"); assertThrows(CatalogoException.class, () -> ValidadorAtencion.cita(datos));
        datos.put("horaFin", "09:00"); datos.put("idProfesional", UUID.randomUUID().toString());
        assertThrows(CatalogoException.class, () -> ValidadorAtencion.cita(datos));
        assertThrows(CatalogoException.class, () -> ValidadorAtencion.estado(Map.of("estado", "programada")));
        assertEquals("no_asistio", ValidadorAtencion.estado(Map.of("estado", "no_asistio")));
    }
}
