package com.dextre.medishift.atencion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.atencion.DatosAtencion.SolicitudCita;
import com.dextre.medishift.atencion.DatosAtencion.SolicitudPaciente;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;

public final class ValidadorAtencion {
    private ValidadorAtencion() { }

    public static SolicitudPaciente paciente(Map<String, Object> datos) {
        var errores = iniciar(datos, Set.of("nombres", "apellidos", "dni", "fechaNacimiento", "correo", "telefono", "activo"));
        String nombres = texto(datos, "nombres", 80, true, errores);
        String apellidos = texto(datos, "apellidos", 100, true, errores);
        String dni = texto(datos, "dni", 8, true, errores);
        String nacimiento = texto(datos, "fechaNacimiento", 10, true, errores);
        String correo = texto(datos, "correo", 254, false, errores).toLowerCase(Locale.ROOT);
        String telefono = texto(datos, "telefono", 20, false, errores);
        if (!dni.matches("[0-9]{8}")) errores.put("dni", "El DNI debe contener exactamente 8 dígitos.");
        LocalDate fecha = convertirFecha(nacimiento, "fechaNacimiento", errores);
        if (!correo.isEmpty() && !correo.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) errores.put("correo", "Ingresa un correo válido.");
        if (!telefono.isEmpty() && (!telefono.matches("\\+?[0-9][0-9 ()-]*")
                || telefono.replaceAll("[^0-9]", "").length() < 7 || telefono.replaceAll("[^0-9]", "").length() > 15)) {
            errores.put("telefono", "Ingresa un teléfono de 7 a 15 dígitos.");
        }
        if (!(datos.get("activo") instanceof Boolean)) errores.put("activo", "Selecciona el estado del paciente.");
        exigir(errores);
        return new SolicitudPaciente(nombres, apellidos, dni, fecha, correo, telefono, Boolean.TRUE.equals(datos.get("activo")));
    }

    public static SolicitudCita cita(Map<String, Object> datos) {
        var errores = iniciar(datos, Set.of("idPaciente", "idTurno", "fecha", "horaInicio", "horaFin"));
        UUID paciente = identificador(datos.get("idPaciente"), "idPaciente", errores);
        UUID turno = identificador(datos.get("idTurno"), "idTurno", errores);
        LocalDate fecha = convertirFecha(texto(datos, "fecha", 10, true, errores), "fecha", errores);
        LocalTime inicio = hora(texto(datos, "horaInicio", 5, true, errores), "horaInicio", errores);
        LocalTime fin = hora(texto(datos, "horaFin", 5, true, errores), "horaFin", errores);
        if (inicio != null && fin != null && !fin.isAfter(inicio)) errores.put("horaFin", "La hora final debe ser posterior a la inicial.");
        exigir(errores);
        return new SolicitudCita(paciente, turno, fecha, inicio, fin);
    }

    public static String estado(Map<String, Object> datos) {
        var errores = iniciar(datos, Set.of("estado"));
        String estado = texto(datos, "estado", 16, true, errores);
        if (!Set.of("atendida", "cancelada", "no_asistio").contains(estado)) errores.put("estado", "Selecciona un estado final válido.");
        exigir(errores);
        return estado;
    }

    public static LocalDate fecha(String texto) {
        var errores = new LinkedHashMap<String, String>();
        LocalDate fecha = convertirFecha(texto, "fecha", errores);
        exigir(errores);
        return fecha;
    }

    public static int pagina(String texto) {
        if (texto == null) return 0;
        try {
            if (texto.matches("[0-9]{1,6}")) return Integer.parseInt(texto);
        } catch (NumberFormatException excepcion) { /* Se devuelve el error público. */ }
        throw new CatalogoException(HttpStatus.BAD_REQUEST, "La página no es válida.", Map.of("pagina", "Usa una página entre 0 y 999999."));
    }

    public static String busqueda(String texto) {
        if (texto == null) return "";
        var errores = new LinkedHashMap<String, String>();
        String resultado = texto(Map.of("buscar", texto), "buscar", 100, false, errores);
        exigir(errores);
        return resultado;
    }

    public static String dni(String valor) {
        if (valor == null || !valor.matches("[0-9]{8}")) {
            throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa el DNI.", Map.of("dni", "Ingresa 8 dígitos."));
        }
        return valor;
    }

    public static String consultaDni(Map<String, Object> datos) {
        var errores = iniciar(datos, Set.of("dni"));
        String documento = texto(datos, "dni", 8, true, errores);
        exigir(errores);
        return dni(documento);
    }

    private static Map<String, String> iniciar(Map<String, Object> datos, Set<String> campos) {
        if (datos == null) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Envía un objeto JSON.", Map.of());
        var errores = new LinkedHashMap<String, String>();
        datos.keySet().stream().filter(campo -> !campos.contains(campo)).forEach(campo -> errores.put(campo, "Campo no admitido."));
        return errores;
    }

    private static String texto(Map<String, Object> datos, String campo, int limite, boolean obligatorio, Map<String, String> errores) {
        Object valor = datos.getOrDefault(campo, "");
        if (!(valor instanceof String contenido)) { errores.put(campo, "Este campo debe contener texto."); return ""; }
        for (int indice = 0; indice < contenido.length(); indice++) {
            char caracter = contenido.charAt(indice);
            if (Character.isHighSurrogate(caracter) && indice + 1 < contenido.length() && Character.isLowSurrogate(contenido.charAt(indice + 1))) indice++;
            else if (Character.isSurrogate(caracter) || Character.isISOControl(caracter)) errores.put(campo, "El texto contiene caracteres no válidos.");
        }
        String normalizado = ValidadorCatalogos.normalizarTexto(contenido);
        if ((obligatorio && normalizado.isBlank()) || normalizado.codePointCount(0, normalizado.length()) > limite) {
            errores.put(campo, "Completa este campo con un máximo de " + limite + " caracteres.");
        }
        return normalizado;
    }

    private static LocalDate convertirFecha(String texto, String campo, Map<String, String> errores) {
        try { if (texto != null && texto.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            LocalDate fecha = LocalDate.parse(texto);
            if (fecha.getYear() >= 1 && fecha.getYear() <= 9999) return fecha;
        } } catch (DateTimeParseException excepcion) { /* Fecha imposible. */ }
        errores.put(campo, "Selecciona una fecha válida entre los años 0001 y 9999."); return null;
    }

    private static LocalTime hora(String texto, String campo, Map<String, String> errores) {
        try { if (texto.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) return LocalTime.parse(texto); }
        catch (DateTimeParseException excepcion) { /* Hora imposible. */ }
        errores.put(campo, "Ingresa una hora válida en formato HH:mm."); return null;
    }

    private static UUID identificador(Object valor, String campo, Map<String, String> errores) {
        try { if (valor instanceof String texto) return ValidadorCatalogos.validarId(texto); }
        catch (CatalogoException excepcion) { /* Se asocia el error con el campo correcto. */ }
        errores.put(campo, "Selecciona un registro válido."); return null;
    }

    private static void exigir(Map<String, String> errores) {
        if (!errores.isEmpty()) throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los campos indicados.", errores);
    }
}
