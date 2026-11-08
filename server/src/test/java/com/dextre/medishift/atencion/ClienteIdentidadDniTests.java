package com.dextre.medishift.atencion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import com.dextre.medishift.catalogos.CatalogoException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClienteIdentidadDniTests {
    private static final String DNI = "00123456";
    private static final String IDENTIDAD = """
            {"dni":"00123456","nombres":"  Ana María ","apellidoPaterno":"Pérez","apellidoMaterno":"Soto","codVerifica":"1"}
            """;
    private HttpClient conexion;
    private ClienteIdentidadDni cliente;

    @BeforeEach
    void preparar() {
        conexion = mock(HttpClient.class);
        cliente = new ClienteIdentidadDni(JsonMapper.builder().build(), "secreto&solo=servidor", conexion);
    }

    @Test
    void consultaContratoOficialSinExponerTokenNiAgregarNacimiento() throws Exception {
        responder(200, IDENTIDAD);
        var identidad = cliente.consultar(DNI);
        assertEquals("Ana María", identidad.nombres()); assertEquals("Pérez Soto", identidad.apellidos());
        var captura = ArgumentCaptor.forClass(HttpRequest.class);
        verify(conexion).send(captura.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("dniruc.apisperu.com", captura.getValue().uri().getHost());
        assertEquals("/api/v1/dni/00123456", captura.getValue().uri().getPath());
        assertEquals("token=secreto%26solo%3Dservidor", captura.getValue().uri().getRawQuery());
        assertEquals(8, captura.getValue().timeout().orElseThrow().toSeconds());
        String publicado = JsonMapper.builder().build().writeValueAsString(identidad);
        assertFalse(publicado.contains("secreto")); assertFalse(publicado.contains("Nacimiento"));
        assertFalse(publicado.contains("codVerifica"));
    }

    @Test
    void noUsaLaRedConDniInvalidoOTokenAusente() {
        assertThrows(CatalogoException.class, () -> cliente.consultar("123"));
        var sinToken = new ClienteIdentidadDni(JsonMapper.builder().build(), "", conexion);
        assertEquals(503, assertThrows(CatalogoException.class, () -> sinToken.consultar(DNI)).obtenerEstado().value());
        verifyNoInteractions(conexion);
    }

    @Test
    void aceptaUnSoloApellidoPeroRechazaDatosDeOtroDocumento() {
        assertEquals("Pérez", cliente.interpretar(IDENTIDAD.replace("\"Soto\"", "null").getBytes(StandardCharsets.UTF_8), DNI).apellidos());
        for (String contenido : new String[]{IDENTIDAD.replace(DNI, "87654321"), "[]", "{}", "no es json",
                IDENTIDAD.replace("\"  Ana María \"", "123"), IDENTIDAD.replace("Pérez", "Pé\\u0000rez")}) {
            assertEquals(503, assertThrows(CatalogoException.class, () -> cliente.interpretar(contenido.getBytes(StandardCharsets.UTF_8), DNI)).obtenerEstado().value());
        }
        assertThrows(CatalogoException.class, () -> cliente.interpretar(new byte[16385], DNI));
    }

    @Test
    void distingueDniSinResultadoDeErrorDeTokenEnRespuestas200() {
        assertEquals(404, assertThrows(CatalogoException.class, () -> cliente.interpretar(
                "{\"success\":false,\"message\":\"DNI no encontrado\"}".getBytes(StandardCharsets.UTF_8), DNI)).obtenerEstado().value());
        var fallo = assertThrows(CatalogoException.class, () -> cliente.interpretar(
                "{\"success\":false,\"message\":\"Token secreto agotado\"}".getBytes(StandardCharsets.UTF_8), DNI));
        assertEquals(503, fallo.obtenerEstado().value()); assertFalse(fallo.getMessage().contains("secreto"));
    }

    @Test
    void erroresHttpNoPublicanCuerpoDelProveedorNiSiguenRedirecciones() throws Exception {
        for (int estado : new int[]{302,401,403,429,500}) {
            responder(estado, "No publicar token secreto ni respuesta interna");
            var fallo = assertThrows(CatalogoException.class, () -> cliente.consultar(DNI));
            assertEquals(503, fallo.obtenerEstado().value()); assertFalse(fallo.getMessage().contains("secreto"));
        }
        responder(404, "No encontrado");
        assertEquals(404, assertThrows(CatalogoException.class, () -> cliente.consultar(DNI)).obtenerEstado().value());
    }

    @Test
    void manejaTimeoutSinPublicarLaUrlConToken() throws Exception {
        when(conexion.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("https://proveedor?token=secreto"));
        assertFalse(assertThrows(CatalogoException.class, () -> cliente.consultar(DNI)).getMessage().contains("secreto"));
    }

    @Test
    void rechazaCamposAjenoAlContratoDeConsulta() {
        assertEquals(DNI, ValidadorAtencion.consultaDni(Map.of("dni", DNI)));
        assertThrows(CatalogoException.class, () -> ValidadorAtencion.consultaDni(Map.of("dni", DNI, "token", "no-admitido")));
    }

    @SuppressWarnings("unchecked")
    private void responder(int estado, String contenido) throws Exception {
        HttpResponse<InputStream> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(estado);
        when(respuesta.body()).thenAnswer(invocacion -> new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)));
        when(conexion.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
    }
}
