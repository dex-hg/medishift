package com.dextre.medishift.programacion.mensual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.ClienteFeriados;
import com.dextre.medishift.feriados.RepositorioFeriados;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioAnual;
import com.dextre.medishift.feriados.DatosFeriados.Feriado;
import com.dextre.medishift.feriados.DatosFeriados.Pais;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Autorización y contratos contra PostgreSQL real; el transporte externo se ensaya por separado. */
@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-planes-mensuales", matches = "true")
class ApiFeriadosPostgresqlTests extends SoporteApiPlanesMensuales {

	private static final Path DIRECTORIO = Path.of("target", "ensayo-feriados-" + UUID.randomUUID()).toAbsolutePath().normalize();
	@MockitoBean private ClienteFeriados proveedor;
	@Autowired private RepositorioFeriados copias;

	@DynamicPropertySource
	static void directorioAislado(DynamicPropertyRegistry propiedades) {
		propiedades.add("medishift.feriados.directorio", DIRECTORIO::toString);
	}

	@BeforeEach
	void prepararFeriados() throws Exception {
		inicializar(institucion, actor);
		for (Pais pais : Pais.values()) { Files.deleteIfExists(DIRECTORIO.resolve(pais.codigo() + "-2026.json")); }
		when(proveedor.consultar(Pais.PERU, 2026)).thenReturn(List.of(
				new Feriado(LocalDate.of(2026, 10, 8), "Ensayo octubre"), new Feriado(LocalDate.of(2026, 11, 1), "Ensayo noviembre")));
	}

	@Test
	void consultaSinCopiaInformaPendienteSinUsarRedNiCambiarDatos() throws Exception {
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2026-11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.feriados.estado").value("Pendiente"))
				.andExpect(jsonPath("$.feriados.codigoPais").value("PE"));
		verifyNoInteractions(proveedor);
	}

	@Test
	void sincronizaElPaisDeSesionYDevuelveSoloLosDiasDelMesSinEscribirTablas() throws Exception {
		int auditorias = contar("audit_event");
		solicitar(post("/api/planificador/feriados/sincronizar").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"periodo\":\"2026-11\"}"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.codigoPais").value("PE")).andExpect(jsonPath("$.estado").value("Disponible"))
				.andExpect(jsonPath("$.minutosPorFeriado").value(360)).andExpect(jsonPath("$.dias.length()").value(1))
				.andExpect(jsonPath("$.dias[0].fecha").value("2026-11-01"));
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2026-10"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.feriados.dias[0].fecha").value("2026-10-08"));
		assertEquals(auditorias, contar("audit_event"));
		verify(proveedor, times(1)).consultar(Pais.PERU, 2026);
	}

	@Test
	void bogotaConsultaColombiaAunqueComparteDesfaseConLima() throws Exception {
		consultas.update("UPDATE institution SET time_zone_institution='America/Bogota' WHERE id_institution=?", institucion);
		when(proveedor.consultar(Pais.COLOMBIA, 2026)).thenReturn(List.of(new Feriado(LocalDate.of(2026, 11, 16), "Ensayo Colombia")));
		solicitar(post("/api/planificador/feriados/sincronizar").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"periodo\":\"2026-11\",\"forzar\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.codigoPais").value("CO"))
				.andExpect(jsonPath("$.dias[0].fecha").value("2026-11-16"));
		verify(proveedor).consultar(Pais.COLOMBIA, 2026);
		verify(proveedor, never()).consultar(Pais.PERU, 2026);
	}

	@Test
	void exigeSesionAdministradorYRechazaPaisHorasYParametrosManipulados() throws Exception {
		solicitar(post("/api/planificador/feriados/sincronizar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"periodo\":\"2026-11\"}")).andExpect(status().isUnauthorized());
		solicitar(post("/api/planificador/feriados/sincronizar").session(sesionDe(institucion, nuevaCuenta(institucion)))
				.contentType(MediaType.APPLICATION_JSON).content("{\"periodo\":\"2026-11\"}")).andExpect(status().isForbidden());
		for (String datos : List.of("{}", "{\"periodo\":\"2026-13\"}", "{\"periodo\":\"2026-11\",\"forzar\":\"true\"}",
				"{\"periodo\":\"2026-11\",\"codigoPais\":\"CO\"}", "{\"periodo\":\"2026-11\",\"minutosPorFeriado\":600}")) {
			solicitar(post("/api/planificador/feriados/sincronizar").session(sesion).contentType(MediaType.APPLICATION_JSON)
					.content(datos)).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(proveedor);
	}

	@Test
	void unFalloExternoConservaLaCopiaAnteriorYNoSePresentaComoCeroFeriados() throws Exception {
		copias.guardar(new CalendarioAnual("PE", 2026, Instant.now().minusSeconds(172800),
				List.of(new Feriado(LocalDate.of(2026, 11, 1), "Copia anterior"))), Pais.PERU);
		when(proveedor.consultar(Pais.PERU, 2026)).thenThrow(CatalogoException.noDisponible("Ensayo sin red"));
		solicitar(post("/api/planificador/feriados/sincronizar").session(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"periodo\":\"2026-11\",\"forzar\":true}")).andExpect(status().isServiceUnavailable());
		solicitar(get("/api/planificador").session(sesion).param("periodo", "2026-11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.feriados.estado").value("Disponible"))
				.andExpect(jsonPath("$.feriados.requiereActualizacion").value(true))
				.andExpect(jsonPath("$.feriados.dias[0].nombre").value("Copia anterior"));
	}

	@AfterAll
	static void retirarCopiaDeEnsayo() throws Exception {
		assertTrue(DIRECTORIO.startsWith(Path.of("target").toAbsolutePath().normalize()));
		assertTrue(DIRECTORIO.getFileName().toString().startsWith("ensayo-feriados-"));
		if (Files.exists(DIRECTORIO)) {
			try (var rutas = Files.walk(DIRECTORIO)) {
				for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) Files.delete(ruta);
			}
		}
	}
}
