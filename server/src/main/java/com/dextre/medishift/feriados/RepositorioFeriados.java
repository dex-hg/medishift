package com.dextre.medishift.feriados;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioAnual;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

/** Copia local de datos públicos por país y año, independiente de las tablas institucionales. */
@Repository
public class RepositorioFeriados {

	private static final Logger REGISTRO = LoggerFactory.getLogger(RepositorioFeriados.class);
	private final Path directorio;
	private final JsonMapper lector;

	public RepositorioFeriados(JsonMapper lector,
			@Value("${medishift.feriados.directorio:./.medishift-cache/feriados}") String directorio) {
		this.lector = lector;
		this.directorio = Path.of(directorio).toAbsolutePath().normalize();
	}

	public Optional<CalendarioAnual> leer(Pais pais, int anio) {
		Path archivo = archivo(pais, anio);
		try {
			if (!Files.isRegularFile(archivo) || Files.size(archivo) > 524_288) { return Optional.empty(); }
			CalendarioAnual calendario = lector.readValue(Files.readString(archivo, StandardCharsets.UTF_8), CalendarioAnual.class);
			validar(calendario, pais, anio);
			return Optional.of(calendario);
		} catch (IOException | RuntimeException fallo) {
			REGISTRO.warn("La copia de feriados {}-{} no es válida; requiere actualización.", pais.codigo(), anio);
			return Optional.empty();
		}
	}

	public void guardar(CalendarioAnual calendario, Pais pais) {
		validar(calendario, pais, calendario.anio());
		Path temporal = null;
		try {
			Files.createDirectories(directorio);
			temporal = Files.createTempFile(directorio, "feriados-", ".tmp");
			Files.writeString(temporal, lector.writeValueAsString(calendario), StandardCharsets.UTF_8);
			Path destino = archivo(pais, calendario.anio());
			try { Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException fallo) { Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException fallo) {
			REGISTRO.debug("Fallo al guardar la copia pública de feriados.", fallo);
			throw CatalogoException.noDisponible("No se pudo guardar la copia local de feriados. Revisa los permisos del directorio configurado.");
		} finally {
			if (temporal != null) {
				try { Files.deleteIfExists(temporal); } catch (IOException fallo) { REGISTRO.warn("No se pudo retirar un archivo temporal de feriados."); }
			}
		}
	}

	private Path archivo(Pais pais, int anio) {
		if (anio < 1 || anio > 9999) { throw new IllegalArgumentException("El año no es válido."); }
		Path archivo = directorio.resolve(pais.codigo() + "-" + anio + ".json").normalize();
		if (!directorio.equals(archivo.getParent())) { throw new IllegalArgumentException("El archivo debe permanecer en el directorio de feriados."); }
		return archivo;
	}

	private void validar(CalendarioAnual calendario, Pais pais, int anio) {
		if (calendario == null || !pais.codigo().equals(calendario.codigoPais()) || calendario.anio() != anio
				|| calendario.actualizado() == null || calendario.actualizado().isAfter(java.time.Instant.now().plusSeconds(300))
				|| calendario.dias() == null || calendario.dias().isEmpty()
				|| calendario.dias().size() > 300) { throw new IllegalArgumentException("El calendario de feriados no es válido."); }
		var fechas = new HashSet<java.time.LocalDate>();
		for (var feriado : calendario.dias()) {
			if (feriado == null || feriado.fecha() == null || feriado.fecha().getYear() != anio || !fechas.add(feriado.fecha())
					|| feriado.nombre() == null || feriado.nombre().isBlank() || feriado.nombre().length() > 1000) {
				throw new IllegalArgumentException("El calendario contiene fechas inválidas o repetidas.");
			}
		}
	}
}
