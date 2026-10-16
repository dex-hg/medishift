package com.dextre.medishift.programacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.DatosProgramacion.Horario;
import com.dextre.medishift.programacion.DatosProgramacion.Requisito;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/** Verifica el límite semanal de las consultas; la ejecución SQL requiere PostgreSQL. */
class RepositorioProgramacionTests {

	private RepositorioProgramacion repositorio;
	private final UUID institucion = UUID.randomUUID();
	private final UUID horario = UUID.randomUUID();
	private final UUID actor = UUID.randomUUID();
	private final LocalDate lunes = LocalDate.of(2027, 2, 1);
	private final List<String> lecturas = new ArrayList<>();
	private final List<String> escrituras = new ArrayList<>();
	private final ArrayDeque<List<?>> respuestas = new ArrayDeque<>();
	private int filasAfectadas;

	@BeforeEach
	void prepararConsultasSinConexion() {
		filasAfectadas = 1;
		JdbcTemplate consultas = mock(JdbcTemplate.class, llamada -> {
			String metodo = llamada.getMethod().getName();
			if ("query".equals(metodo)) {
				lecturas.add(llamada.getArgument(0));
				return respuestas.isEmpty() ? List.of() : respuestas.removeFirst();
			}
			if ("queryForObject".equals(metodo)) {
				lecturas.add(llamada.getArgument(0));
				return Boolean.class.equals(llamada.getArgument(1)) ? Boolean.FALSE : Integer.valueOf(1);
			}
			if ("update".equals(metodo)) {
				escrituras.add(llamada.getArgument(0));
				return filasAfectadas;
			}
			return RETURNS_DEFAULTS.answer(llamada);
		});
		repositorio = new RepositorioProgramacion(consultas);
	}

	@Test
	void consultasPorPeriodoSoloBuscanVersionesSemanales() {
		assertTrue(repositorio.operativo(institucion, lunes).isEmpty());
		assertTrue(repositorio.aprobado(institucion, lunes).isEmpty());
		assertEquals(2, lecturas.size());
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
	}

	@Test
	void horarioYTurnoPorIdentificadorInaccesiblesDevuelven404() {
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.buscarHorario(institucion, horario)).obtenerEstado().value());
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.buscarTurno(institucion, UUID.randomUUID())).obtenerEstado().value());
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
		assertTrue(lecturas.get(1).contains("h.id_institution = t.id_institution"));
	}

	@Test
	void turnosYRequisitosDeUnHorarioExcluyenContenidoMensual() {
		assertTrue(repositorio.turnos(institucion, horario).isEmpty());
		assertTrue(repositorio.requisitos(horario).isEmpty());
		assertEquals(2, lecturas.size());
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
	}

	@Test
	void nuevaVersionCuentaSoloSemanasYConservaInsertCompatibleConEsquemaAnterior() {
		respuestas.add(List.of(horario("draft")));
		assertNotNull(repositorio.crearHorario(institucion, actor, lunes));
		assertTrue(lecturas.getFirst().contains("MAX(version_schedule)"));
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
		assertEquals(1, escrituras.size());
		assertFalse(escrituras.getFirst().contains("period_type_schedule"));
		assertFalse(escrituras.getFirst().contains("id_planning_unit"));
	}

	@Test
	void lecturasDeProteccionConservanTodosLosPeriodos() {
		Instant inicio = Instant.parse("2027-02-01T05:00:00Z");
		Instant fin = inicio.plusSeconds(7 * 24 * 60 * 60);
		repositorio.futuros(institucion, null);
		repositorio.futuros(institucion, UUID.randomUUID());
		repositorio.aprobadosEn(institucion, inicio, fin, null);
		repositorio.aprobadosEn(institucion, inicio, fin, horario);
		assertEquals(4, lecturas.size());
		for (String consulta : lecturas) {
			assertFalse(consulta.contains("period_type_schedule"));
			assertTrue(consulta.contains("t.id_institution = ?"));
		}
		assertTrue(lecturas.getLast().contains("t.id_schedule <> ?"));
	}

	@Test
	void insertarTurnoExigeHorarioSemanalDeLaInstitucionYEnBorrador() {
		repositorio.insertarTurno(institucion, turno());
		String sentencia = escrituras.getFirst();
		comprobarFiltroSemanalCompatible(sentencia);
		assertTrue(sentencia.contains("SELECT ?, ?, h.id_schedule"));
		assertTrue(sentencia.contains("h.id_institution = ? AND h.id_schedule = ?"));
		assertTrue(sentencia.contains("h.status_schedule = 'draft'"));
	}

	@Test
	void insercionSinHorarioSemanalNoAparentaExito() {
		filasAfectadas = 0;
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.insertarTurno(institucion, turno())).obtenerEstado().value());
		assertEquals(1, escrituras.size());
	}

	@Test
	void editarYEliminarTurnosFiltranModalidadYConservanRechazoPorRevision() {
		filasAfectadas = 0;
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> repositorio.actualizarTurno(institucion, turno())).obtenerEstado().value());
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> repositorio.eliminarTurno(institucion, turno().id())).obtenerEstado().value());
		assertEquals(2, escrituras.size());
		for (String sentencia : escrituras) {
			comprobarFiltroSemanalCompatible(sentencia);
			assertTrue(sentencia.contains("h.id_institution = shift.id_institution"));
			assertTrue(sentencia.contains("h.status_schedule = 'draft'"));
		}
	}

	@Test
	void aprobarOCambiarEstadoDeHorarioInaccesibleNoModificaTurnos() {
		filasAfectadas = 0;
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.aprobar(institucion, horario, actor)).obtenerEstado().value());
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.cambiarEstado(institucion, horario, "cancelled")).obtenerEstado().value());
		assertEquals(2, escrituras.size());
		for (String sentencia : escrituras) {
			assertTrue(sentencia.startsWith("UPDATE schedule h"));
			comprobarFiltroSemanalCompatible(sentencia);
		}
	}

	@Test
	void aprobarYCambiarEstadoFiltranTambienLaEscrituraDeTurnos() {
		repositorio.aprobar(institucion, horario, actor);
		repositorio.cambiarEstado(institucion, horario, "superseded");
		assertEquals(4, escrituras.size());
		escrituras.forEach(this::comprobarFiltroSemanalCompatible);
		assertTrue(escrituras.get(1).contains("h.id_institution = t.id_institution"));
		assertTrue(escrituras.get(3).contains("h.id_institution = t.id_institution"));
	}

	@Test
	void guardaDeEjecucionRechazaHorarioFueraDelContratoSemanal() {
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.verificarSinEjecucion(institucion, horario)).obtenerEstado().value());
		assertEquals(1, lecturas.size());
		comprobarFiltroSemanalCompatible(lecturas.getFirst());
		assertTrue(escrituras.isEmpty());
	}

	@Test
	void copiaRechazaDestinoMensualAntesDeEscribirContenido() {
		respuestas.add(List.of(horario("approved")));
		respuestas.add(List.of());
		assertEquals(404, assertThrows(CatalogoException.class,
				() -> repositorio.copiarContenido(institucion, horario, UUID.randomUUID())).obtenerEstado().value());
		assertEquals(2, lecturas.size());
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
		assertTrue(escrituras.isEmpty());
	}

	@Test
	void copiaRechazaDestinoAprobadoAntesDeEscribirContenido() {
		respuestas.add(List.of(horario("approved")));
		respuestas.add(List.of(horario("approved")));
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> repositorio.copiarContenido(institucion, horario, UUID.randomUUID())).obtenerEstado().value());
		assertTrue(escrituras.isEmpty());
	}

	@Test
	void copiaDeTurnosYCoberturaMantieneGuardasEnOrigenYDestino() {
		respuestas.add(List.of(horario("approved")));
		respuestas.add(List.of(horario("draft")));
		respuestas.add(List.of(turno()));
		respuestas.add(List.of(new Requisito(turno().especialidad(), turno().inicio(), turno().fin(), 1)));
		repositorio.copiarContenido(institucion, horario, UUID.randomUUID());
		assertEquals(4, lecturas.size());
		assertEquals(2, escrituras.size());
		lecturas.forEach(this::comprobarFiltroSemanalCompatible);
		escrituras.forEach(this::comprobarFiltroSemanalCompatible);
		assertTrue(escrituras.getLast().contains("h.status_schedule = 'draft'"));
	}

	private void comprobarFiltroSemanalCompatible(String sentencia) {
		assertTrue(sentencia.contains("COALESCE(to_jsonb(h)->>'period_type_schedule', 'weekly') = 'weekly'"), sentencia);
	}

	private Horario horario(String estado) {
		return new Horario(horario, institucion, lunes, lunes.plusDays(6), 1, estado,
				actor, null, Instant.parse("2027-01-01T00:00:00Z"), null);
	}

	private Turno turno() {
		return new Turno(UUID.randomUUID(), horario, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				Instant.parse("2027-02-01T13:00:00Z"), Instant.parse("2027-02-01T15:00:00Z"),
				"draft", "", "Temporal", "Sala", "Especialidad");
	}
}
