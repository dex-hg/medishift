package com.dextre.medishift.seguridad;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.sesion.SesionesHttp;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroSeguridadApi extends OncePerRequestFilter {

	static final int MAXIMO_CUERPO = 32 * 1024;
	private static final int MAXIMO_CONSULTA = 2048;

	private final PoliticaOrigenApi politicaOrigen;
	private final boolean httpsPublico;
	private final JsonMapper json;

	public FiltroSeguridadApi(@Value("${medishift.origenes-permitidos}") String permitidos,
			@Value("${server.servlet.session.cookie.secure:false}") boolean httpsPublico, JsonMapper json) {
		this.politicaOrigen = new PoliticaOrigenApi(permitidos);
		this.httpsPublico = httpsPublico;
		this.json = json;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain filtros)
			throws ServletException, IOException {
		aplicarCabeceras(respuesta, httpsPublico || solicitud.isSecure());
		if (!politicaOrigen.permite(solicitud)) {
			rechazar(respuesta, HttpStatus.FORBIDDEN, "El origen de la solicitud no está permitido.");
			return;
		}
		if (solicitud.getContentLengthLong() > MAXIMO_CUERPO) {
			rechazar(respuesta, HttpStatus.CONTENT_TOO_LARGE, "El cuerpo de la solicitud supera los 32 KiB.");
			return;
		}
		HttpServletRequest solicitudVerificada = solicitud;
		if (!PoliticaOrigenApi.esMetodoSeguro(solicitud)) {
			byte[] cuerpo = solicitud.getInputStream().readNBytes(MAXIMO_CUERPO + 1);
			if (cuerpo.length > MAXIMO_CUERPO) {
				rechazar(respuesta, HttpStatus.CONTENT_TOO_LARGE, "El cuerpo de la solicitud supera los 32 KiB.");
				return;
			}
			if (cuerpo.length > 0 && !esJsonSinCompresion(solicitud)) {
				rechazar(respuesta, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
						"Envía un cuerpo JSON sin compresión y con codificación UTF-8.");
				return;
			}
			solicitudVerificada = new SolicitudConCuerpo(solicitud, cuerpo);
		}
		if (!consultaValida(solicitudVerificada)) {
			rechazar(respuesta, HttpStatus.BAD_REQUEST, "Revisa los parámetros de la solicitud.");
			return;
		}
		SesionesHttp.expirarSiCorresponde(solicitudVerificada.getSession(false));
		filtros.doFilter(solicitudVerificada, respuesta);
	}

	private static boolean consultaValida(HttpServletRequest solicitud) {
		String consulta = solicitud.getQueryString();
		if (consulta == null) return true;
		if (consulta.length() > MAXIMO_CONSULTA) return false;
		try {
			return solicitud.getParameterMap().values().stream().allMatch(valores -> valores.length == 1);
		} catch (IllegalArgumentException | IllegalStateException fallo) {
			return false;
		}
	}

	private static boolean esJsonSinCompresion(HttpServletRequest solicitud) {
		String codificacion = solicitud.getHeader(HttpHeaders.CONTENT_ENCODING);
		if (codificacion != null && !"identity".equalsIgnoreCase(codificacion)) return false;
		try {
			MediaType tipo = MediaType.parseMediaType(solicitud.getContentType());
			return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(tipo)
					&& (tipo.getCharset() == null || StandardCharsets.UTF_8.equals(tipo.getCharset()));
		} catch (IllegalArgumentException fallo) {
			return false;
		}
	}

	private static void aplicarCabeceras(HttpServletResponse respuesta, boolean https) {
		respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		respuesta.setHeader("X-Content-Type-Options", "nosniff");
		respuesta.setHeader("X-Frame-Options", "DENY");
		respuesta.setHeader("Referrer-Policy", "no-referrer");
		respuesta.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
		if (https) respuesta.setHeader("Strict-Transport-Security", "max-age=31536000");
	}

	private void rechazar(HttpServletResponse respuesta, HttpStatus estado, String mensaje) throws IOException {
		respuesta.setStatus(estado.value());
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		json.writeValue(respuesta.getOutputStream(), Map.of("mensaje", mensaje, "errores", Map.of()));
	}

	private static final class SolicitudConCuerpo extends HttpServletRequestWrapper {
		private final ServletInputStream contenido;

		SolicitudConCuerpo(HttpServletRequest solicitud, byte[] cuerpo) {
			super(solicitud);
			var entrada = new ByteArrayInputStream(cuerpo);
			contenido = new ServletInputStream() {
				@Override
				public int read() { return entrada.read(); }

				@Override
				public int read(byte[] destino, int inicio, int longitud) { return entrada.read(destino, inicio, longitud); }

				@Override
				public boolean isFinished() { return entrada.available() == 0; }

				@Override
				public boolean isReady() { return true; }

				@Override
				public void setReadListener(ReadListener escucha) {
					throw new IllegalStateException("El cuerpo JSON se lee de forma síncrona.");
				}
			};
		}

		@Override
		public ServletInputStream getInputStream() { return contenido; }

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(contenido, StandardCharsets.UTF_8));
		}
	}
}
