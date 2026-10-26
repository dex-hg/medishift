package com.dextre.medishift.feriados;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.DatosFeriados.Feriado;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

/** Adaptador de la API pública; nunca transmite datos de instituciones o profesionales. */
@Repository
public class ClienteFeriados {

	public static final String FUENTE = "Nager.Holidays Community v4";
	private static final int MAXIMO_BYTES = 524_288;
	private final JsonMapper lector;

	public ClienteFeriados(JsonMapper lector) { this.lector = lector; }

	public List<Feriado> consultar(Pais pais, int anio) {
		int actual = Year.now(java.time.ZoneOffset.UTC).getValue();
		if (anio < actual || anio > actual + 5) {
			throw CatalogoException.conflicto("periodo", "La API gratuita consulta desde " + actual + " hasta " + (actual + 5)
					+ ". Los años guardados previamente siguen disponibles en la copia local.");
		}
		HttpRequest solicitud = HttpRequest.newBuilder(URI.create("https://nagerholidays.com/api/v4/Holidays/"
				+ pais.codigo() + "/" + anio)).timeout(Duration.ofSeconds(9)).header("Accept", "application/json")
				.header("User-Agent", "MediShift/1.0 (academic scheduling)").GET().build();
		try {
			HttpResponse<byte[]> respuesta = Conexion.CLIENTE.send(solicitud, HttpResponse.BodyHandlers.ofByteArray());
			if (respuesta.statusCode() != 200 || respuesta.body().length > MAXIMO_BYTES) { throw noDisponible(); }
			return interpretar(respuesta.body(), pais, anio);
		} catch (InterruptedException fallo) {
			Thread.currentThread().interrupt();
			throw noDisponible();
		} catch (IOException fallo) { throw noDisponible(); }
	}

	/** Se valida también lo que no se importará, para detectar respuestas incompletas o de otro país. */
	public List<Feriado> interpretar(byte[] contenido, Pais pais, int anio) {
		try {
			EntradaProveedor[] entradas = lector.readValue(contenido, EntradaProveedor[].class);
			if (entradas == null || entradas.length == 0 || entradas.length > 300) { throw noDisponible(); }
			Map<LocalDate, TreeSet<String>> fechas = new TreeMap<>();
			for (EntradaProveedor entrada : entradas) {
				if (entrada == null || entrada.fecha() == null || entrada.fecha().getYear() != anio
						|| !pais.codigo().equals(entrada.codigoPais()) || entrada.nombre() == null || entrada.nombre().isBlank()
						|| entrada.nombre().length() > 240 || entrada.nacional() == null || entrada.tipos() == null
						|| Arrays.stream(entrada.tipos()).anyMatch(tipo -> tipo == null || tipo.isBlank())) { throw noDisponible(); }
				if (!entrada.nacional() || !Arrays.asList(entrada.tipos()).contains("Public")
						|| (entrada.subdivisiones() != null && entrada.subdivisiones().length > 0)) { continue; }
				String nombre = entrada.nombre().replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
				if (nombre.isBlank()) { throw noDisponible(); }
				// Corrección documentada: Perú reconoce Jueves y Viernes Santo, no Domingo de Resurrección.
				// https://elperuano.pe/noticia/291141-semana-santa-2026-en-peru-cuando-sera-el-proximo-fin-de-semana-largo
				if (pais == Pais.PERU && "Easter Sunday".equalsIgnoreCase(nombre)) { continue; }
				fechas.computeIfAbsent(entrada.fecha(), fecha -> new TreeSet<>()).add(nombre);
			}
			if (fechas.isEmpty()) { throw noDisponible(); }
			return fechas.entrySet().stream().map(fecha -> new Feriado(fecha.getKey(), String.join(" / ", fecha.getValue()))).toList();
		} catch (CatalogoException fallo) { throw fallo; }
		catch (RuntimeException fallo) { throw noDisponible(); }
	}

	private static CatalogoException noDisponible() {
		return CatalogoException.noDisponible("No se pudieron verificar los feriados. Se conserva la copia local anterior; vuelve a intentar la actualización.");
	}

	private static final class Conexion {
		private static final HttpClient CLIENTE = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EntradaProveedor(@JsonProperty("date") LocalDate fecha, @JsonProperty("name") String nombre,
			@JsonProperty("countryCode") String codigoPais, @JsonProperty("nationalHoliday") Boolean nacional,
			@JsonProperty("subdivisionCodes") String[] subdivisiones, @JsonProperty("holidayTypes") String[] tipos) { }
}
