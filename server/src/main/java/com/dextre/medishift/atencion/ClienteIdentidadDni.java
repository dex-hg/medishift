package com.dextre.medishift.atencion;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.atencion.DatosAtencion.IdentidadDni;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;

/** El token y la respuesta original del proveedor nunca se envían al navegador ni al log. */
@Repository
public class ClienteIdentidadDni {
    private static final Logger REGISTRO = LoggerFactory.getLogger(ClienteIdentidadDni.class);
    private static final Duration PLAZO_PROVEEDOR = Duration.ofSeconds(8);
    private static final int MAXIMO_BYTES = 16_384;
    private final JsonMapper lector;
    private final String token;
    private final HttpClient conexion;

    @Autowired
    public ClienteIdentidadDni(JsonMapper lector, @Value("${MEDISHIFT_APISPERU_TOKEN:}") String token) {
        this(lector, token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    ClienteIdentidadDni(JsonMapper lector, String token, HttpClient conexion) {
        this.lector = lector;
        this.token = token == null ? "" : token.strip();
        this.conexion = conexion;
    }

    public IdentidadDni consultar(String dni) {
        ValidadorAtencion.dni(dni);
        if (token.isBlank() || token.length() > 8192) {
            throw CatalogoException.noDisponible("La consulta de DNI no está configurada. Puedes completar los nombres manualmente.");
        }
        URI direccion = URI.create("https://dniruc.apisperu.com/api/v1/dni/" + dni
                + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
        HttpRequest solicitud = HttpRequest.newBuilder(direccion).timeout(PLAZO_PROVEEDOR)
                .header("Accept", "application/json").GET().build();
        var pendiente = conexion.sendAsync(solicitud, ClienteIdentidadDni::recibirCuerpo);
        try {
            // La espera incluye la descarga completa y cancela el intercambio al vencer.
            HttpResponse<byte[]> respuesta = pendiente.get(PLAZO_PROVEEDOR.toMillis(), TimeUnit.MILLISECONDS);
            if (respuesta.statusCode() == 404) throw noEncontrado();
            if (respuesta.statusCode() != 200) throw noDisponible();
            return interpretar(respuesta.body(), dni);
        } catch (TimeoutException fallo) {
            pendiente.cancel(true);
            throw tiempoAgotado();
        } catch (InterruptedException fallo) {
            pendiente.cancel(true);
            Thread.currentThread().interrupt();
            throw noDisponible();
        } catch (ExecutionException fallo) {
            if (fallo.getCause() instanceof HttpTimeoutException) throw tiempoAgotado();
            throw noDisponible();
        }
    }

    private static HttpResponse.BodySubscriber<byte[]> recibirCuerpo(HttpResponse.ResponseInfo respuesta) {
        return respuesta.statusCode() == 200 ? new CuerpoAcotado() : HttpResponse.BodySubscribers.replacing(null);
    }

    public IdentidadDni interpretar(byte[] contenido, String dni) {
        try {
            if (contenido == null || contenido.length == 0 || contenido.length > MAXIMO_BYTES) throw noDisponible();
            Map<?, ?> datos = lector.readValue(contenido, Map.class);
            if (datos == null) throw noDisponible();
            if (Boolean.FALSE.equals(datos.get("success"))) {
                String mensaje = datos.get("message") instanceof String texto ? texto.toLowerCase(Locale.ROOT) : "";
                if (mensaje.matches("(?s).*(token|autentic|autoriz|cuota|límite|limite).*")) throw noDisponible();
                if (mensaje.matches("(?s).*(no encontrad|no existe|sin resultado).*")) throw noEncontrado();
                throw noDisponible();
            }
            if (!dni.equals(datos.get("dni"))) throw noDisponible();
            String nombres = texto(datos.get("nombres"), 80, true);
            String paterno = texto(datos.get("apellidoPaterno"), 100, false);
            String materno = texto(datos.get("apellidoMaterno"), 100, false);
            String apellidos = texto((paterno + " " + materno).strip(), 100, true);
            return new IdentidadDni(dni, nombres, apellidos);
        } catch (CatalogoException fallo) {
            throw fallo;
        } catch (RuntimeException fallo) {
            throw noDisponible();
        }
    }

    private static String texto(Object valor, int limite, boolean obligatorio) {
        if (valor == null && !obligatorio) return "";
        if (!(valor instanceof String contenido) || contenido.codePoints().anyMatch(caracter ->
                Character.isISOControl(caracter) || (caracter >= 0xD800 && caracter <= 0xDFFF))) throw noDisponible();
        String normalizado = ValidadorCatalogos.normalizarTexto(contenido);
        if ((obligatorio && normalizado.isBlank()) || normalizado.codePointCount(0, normalizado.length()) > limite) throw noDisponible();
        return normalizado;
    }

    private static CatalogoException noEncontrado() {
        return new CatalogoException(HttpStatus.NOT_FOUND,
                "No se encontraron datos para este DNI. Puedes completar los nombres manualmente.", Map.of());
    }

    private static CatalogoException noDisponible() {
        return CatalogoException.noDisponible("No se pudo consultar el DNI en este momento. Puedes completar los nombres manualmente.");
    }

    private static CatalogoException tiempoAgotado() {
        REGISTRO.warn("El proveedor de DNI excedió el plazo de consulta.");
        return new CatalogoException(HttpStatus.GATEWAY_TIMEOUT,
                "La consulta de DNI tardó demasiado. Puedes completar los nombres manualmente.", Map.of());
    }

    private static final class CuerpoAcotado implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> receptor = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription suscripcion;
        private int restantes = MAXIMO_BYTES;

        @Override
        public CompletionStage<byte[]> getBody() {
            return receptor.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription suscripcion) {
            this.suscripcion = suscripcion;
            receptor.onSubscribe(suscripcion);
        }

        @Override
        public void onNext(List<ByteBuffer> partes) {
            for (ByteBuffer parte : partes) {
                if (parte.remaining() > restantes) {
                    suscripcion.cancel();
                    receptor.onError(new IOException("La respuesta excede el límite de tamaño."));
                    return;
                }
                restantes -= parte.remaining();
            }
            receptor.onNext(partes);
        }

        @Override
        public void onError(Throwable fallo) {
            receptor.onError(fallo);
        }

        @Override
        public void onComplete() {
            receptor.onComplete();
        }
    }
}
