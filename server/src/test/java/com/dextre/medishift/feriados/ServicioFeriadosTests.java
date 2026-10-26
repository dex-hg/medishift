package com.dextre.medishift.feriados;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.feriados.DatosFeriados.CalendarioAnual;
import com.dextre.medishift.feriados.DatosFeriados.Feriado;
import com.dextre.medishift.feriados.DatosFeriados.Pais;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.mensual.PeriodoMensual;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioFeriadosTests {

	@TempDir Path directorio;
	private final UUID institucion = UUID.randomUUID();
	private final UUID actor = UUID.randomUUID();
	private final ClienteFeriados proveedor = mock(ClienteFeriados.class);
	private final ServicioAccesoMensual acceso = mock(ServicioAccesoMensual.class);
	private final RepositorioProgramacion horarios = mock(RepositorioProgramacion.class);

	@Test
	void unaConsultaSinCopiaQuedaPendienteSinLlamarAlServicioExterno() {
		var resultado = servicio().consultar("America/Lima", new PeriodoMensual(2026, 11));
		assertEquals("PE", resultado.codigoPais());
		assertEquals("Pendiente", resultado.estado());
		assertTrue(resultado.requiereActualizacion());
		assertEquals(360, resultado.minutosPorFeriado());
		verifyNoInteractions(proveedor, acceso, horarios);
	}

	@Test
	void consultaUnaCopiaHistoricaSinConexionYConservaLaFechaLocalDelFeriado() {
		repositorio().guardar(new CalendarioAnual("PE", 2020, Instant.parse("2020-11-01T12:00:00Z"),
				List.of(new Feriado(LocalDate.of(2020, 11, 1), "Histórico de ensayo"))), Pais.PERU);
		var resultado = servicio().consultar("America/Lima", new PeriodoMensual(2020, 11));
		assertEquals("Disponible", resultado.estado());
		assertEquals("2020-11-01", resultado.dias().getFirst().fecha().toString());
		assertTrue(resultado.requiereActualizacion());
		assertEquals("Pendiente", servicio().consultar("UTC", new PeriodoMensual(2020, 11)).estado());
		verifyNoInteractions(proveedor);
	}

	@Test
	void dosSolicitudesSimultaneasSoloDescarganUnaVezYLaActualizacionExigeAdministrador() throws Exception {
		when(horarios.zona(institucion)).thenReturn(ZoneId.of("America/Lima"));
		when(proveedor.consultar(Pais.PERU, 2026)).thenReturn(List.of(new Feriado(LocalDate.of(2026, 11, 1), "Ensayo")));
		var servicio = servicio();
		try (var ejecutores = Executors.newFixedThreadPool(2)) {
			var primera = ejecutores.submit(() -> servicio.actualizar(institucion, actor, new PeriodoMensual(2026, 11), false));
			var segunda = ejecutores.submit(() -> servicio.actualizar(institucion, actor, new PeriodoMensual(2026, 11), false));
			assertEquals("Disponible", primera.get(10, TimeUnit.SECONDS).estado());
			assertEquals("Disponible", segunda.get(10, TimeUnit.SECONDS).estado());
		}
		verify(proveedor, times(1)).consultar(Pais.PERU, 2026);
		verify(acceso, times(2)).exigirAdministrador(institucion, actor);
	}

	@Test
	void conservaElCalendarioAnteriorSiElProveedorFalla() {
		repositorio().guardar(new CalendarioAnual("PE", 2026, Instant.parse("2026-01-01T00:00:00Z"),
				List.of(new Feriado(LocalDate.of(2026, 11, 1), "Copia anterior"))), Pais.PERU);
		when(horarios.zona(institucion)).thenReturn(ZoneId.of("America/Lima"));
		when(proveedor.consultar(Pais.PERU, 2026)).thenThrow(CatalogoException.noDisponible("Proveedor inaccesible"));
		assertThrows(CatalogoException.class, () -> servicio().actualizar(institucion, actor, new PeriodoMensual(2026, 11), true));
		assertEquals("Copia anterior", servicio().consultar("America/Lima", new PeriodoMensual(2026, 11)).dias().getFirst().nombre());
	}

	private RepositorioFeriados repositorio() { return new RepositorioFeriados(JsonMapper.builder().build(), directorio.toString()); }
	private ServicioFeriados servicio() { return new ServicioFeriados(repositorio(), proveedor, acceso, horarios); }
}
