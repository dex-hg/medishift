package com.dextre.medishift.seguridad;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

final class PoliticaOrigenApi {

	private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");
	private final Set<Origen> permitidos;

	PoliticaOrigenApi(String configuracion) {
		permitidos = Arrays.stream(configuracion.split(","))
				.map(String::strip)
				.filter(valor -> !valor.isEmpty())
				.map(valor -> {
					Origen origen = leer(valor, false);
					if (origen == null) {
						throw new IllegalArgumentException("MEDISHIFT_ORIGENES_PERMITIDOS requiere orígenes HTTP(S) sin rutas.");
					}
					return origen;
				})
				.collect(Collectors.toUnmodifiableSet());
	}

	boolean permite(HttpServletRequest solicitud) {
		if (esMetodoSeguro(solicitud)) return true;
		var encabezados = solicitud.getHeaders("Origin");
		if (encabezados.hasMoreElements()) {
			Origen origen = leer(encabezados.nextElement(), false);
			return !encabezados.hasMoreElements() && coincide(solicitud, origen);
		}
		String referente = solicitud.getHeader("Referer");
		if (referente != null) return coincide(solicitud, leer(referente, true));

		String sitio = solicitud.getHeader("Sec-Fetch-Site");
		return "same-origin".equalsIgnoreCase(sitio)
				|| (sitio == null && solicitud.getHeader("Cookie") == null);
	}

	static boolean esMetodoSeguro(HttpServletRequest solicitud) {
		return METODOS_SEGUROS.contains(solicitud.getMethod());
	}

	private boolean coincide(HttpServletRequest solicitud, Origen origen) {
		if (origen == null) return false;
		Origen destino = new Origen(solicitud.getScheme(), solicitud.getServerName(), solicitud.getServerPort());
		return permitidos.contains(origen) || destino.equals(origen);
	}

	private static Origen leer(String valor, boolean admiteRuta) {
		try {
			URI uri = URI.create(valor);
			String esquema = uri.getScheme();
			if (!("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema))
					|| uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
					|| (!admiteRuta && (uri.getRawQuery() != null || !uri.getRawPath().isEmpty()))) {
				return null;
			}
			int puerto = uri.getPort() == -1 ? ("https".equalsIgnoreCase(esquema) ? 443 : 80) : uri.getPort();
			return puerto >= 1 && puerto <= 65535 ? new Origen(esquema, uri.getHost(), puerto) : null;
		} catch (IllegalArgumentException fallo) {
			return null;
		}
	}

	private record Origen(String esquema, String host, int puerto) {
		Origen {
			esquema = esquema.toLowerCase(Locale.ROOT);
			host = host.toLowerCase(Locale.ROOT);
			if (host.startsWith("[") && host.endsWith("]")) {
				host = host.substring(1, host.length() - 1);
			}
		}
	}
}
