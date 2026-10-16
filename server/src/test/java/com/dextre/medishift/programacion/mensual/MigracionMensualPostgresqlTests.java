package com.dextre.medishift.programacion.mensual;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "medishift.prueba-migracion-mensual", matches = "true")
class MigracionMensualPostgresqlTests extends SoporteMigracionMensual {

	@Test
	void conservaIdentidadEstadosYPeriodosHistoricosSinReasignarlos() throws Exception {
		horarioSemanal(inicio.minusDays(4), inicio.plusDays(9), 1, "draft");
		horarioSemanal(inicio, inicio.plusDays(6), 2, "draft");
		String originales = filasOriginales();
		migrar();
		assertEquals(originales, filasOriginales());
		assertEquals(3, consultas.queryForObject("""
				SELECT count(*) FROM schedule WHERE period_type_schedule = 'weekly' AND id_planning_unit IS NULL
				""", Integer.class));
		assertEquals(0, consultas.queryForObject("SELECT count(*) FROM planning_unit", Integer.class));
		assertEquals(legado, consultas.queryForObject("SELECT id_schedule FROM shift", UUID.class));
	}

	@Test
	void dosServiciosPuedenTenerSuPropiaAprobacionDelMismoMes() throws Exception {
		migrar();
		UUID primera = nuevaUnidad(institucion, "U01");
		UUID segunda = nuevaUnidad(institucion, "U02");
		UUID plan = planMensual(primera, inicio, inicio.withDayOfMonth(30), 1, "approved");
		planMensual(segunda, inicio, inicio.withDayOfMonth(30), 1, "approved");
		assertEquals(2, consultas.queryForObject("""
				SELECT count(*) FROM schedule WHERE period_type_schedule = 'monthly' AND status_schedule = 'approved'
				""", Integer.class));
		assertEquals(primera, consultas.queryForObject("SELECT id_planning_unit FROM schedule WHERE id_schedule = ?",
				UUID.class, plan));
	}

	@ParameterizedTest
	@CsvSource({"2032-02-01,2032-02-29", "2031-02-01,2031-02-28", "2032-04-01,2032-04-30",
			"2032-12-01,2032-12-31", "9999-12-01,9999-12-31", "0001-01-01,0001-01-31"})
	void aceptaSolamenteMesesCompletosIncluidoBisiestoYExtremos(LocalDate desde, LocalDate hasta) throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		UUID plan = planMensual(unidad, desde, hasta, 1, "draft");
		assertEquals(hasta, consultas.queryForObject("SELECT period_end_schedule FROM schedule WHERE id_schedule = ?",
				LocalDate.class, plan));
		rechaza("23514", () -> planMensual(unidad, desde.plusDays(1), hasta, 2, "draft"));
		rechaza("23514", () -> planMensual(unidad, desde, hasta.minusDays(1), 2, "draft"));
	}

	@Test
	void rechazaReferenciasNulasModalidadInvalidaYUnidadEnHorarioSemanal() throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		rechaza("23514", () -> planMensual(null, inicio, inicio.withDayOfMonth(30), 1, "draft"));
		rechaza("23514", () -> consultas.update("UPDATE schedule SET period_type_schedule = 'other' WHERE id_schedule = ?", legado));
		rechaza("23514", () -> consultas.update("UPDATE schedule SET id_planning_unit = ? WHERE id_schedule = ?", unidad, legado));
		rechaza("23514", () -> planMensual(unidad, LocalDate.of(10000, 1, 1), LocalDate.of(10000, 1, 31), 1, "draft"));
	}

	@Test
	void unidadCreadorYAprobadorDebenPertenecerALaInstitucion() throws Exception {
		migrar();
		UUID ajena = nuevaInstitucion();
		UUID actorAjeno = nuevoActor(ajena);
		UUID unidadAjena = nuevaUnidad(ajena, "U01");
		rechaza("23503", () -> planMensual(unidadAjena, inicio, inicio.withDayOfMonth(30), 1, "draft"));
		UUID unidad = nuevaUnidad(institucion, "U01");
		UUID plan = planMensual(unidad, inicio, inicio.withDayOfMonth(30), 1, "draft");
		rechaza("23503", () -> consultas.update("UPDATE schedule SET id_creator_user_account = ? WHERE id_schedule = ?", actorAjeno, plan));
		rechaza("23503", () -> consultas.update("UPDATE schedule SET id_approver_user_account = ? WHERE id_schedule = ?", actorAjeno, plan));
	}

	@Test
	void conservaUnicidadDeVersionYAprobacionSemanal() throws Exception {
		migrar();
		rechaza("23505", () -> horarioSemanal(inicio, inicio.plusDays(6), 1, "draft"));
		rechaza("23505", () -> horarioSemanal(inicio, inicio.plusDays(6), 2, "approved"));
		horarioSemanal(inicio, inicio.plusDays(6), 2, "draft");
	}

	@Test
	void versionMensualEsUnicaYConservaHistorialAlSustituirAprobacion() throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		UUID anterior = planMensual(unidad, inicio, inicio.withDayOfMonth(30), 1, "approved");
		rechaza("23505", () -> planMensual(unidad, inicio, inicio.withDayOfMonth(30), 1, "draft"));
		rechaza("23505", () -> planMensual(unidad, inicio, inicio.withDayOfMonth(30), 2, "approved"));
		UUID borrador = planMensual(unidad, inicio, inicio.withDayOfMonth(30), 2, "draft");
		consultas.update("UPDATE schedule SET status_schedule = 'superseded' WHERE id_schedule = ?", anterior);
		consultas.update("UPDATE schedule SET status_schedule = 'approved' WHERE id_schedule = ?", borrador);
		assertEquals("superseded", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?", String.class, anterior));
		assertEquals(2, consultas.queryForObject("SELECT count(*) FROM schedule WHERE id_planning_unit = ?", Integer.class, unidad));
	}

	@Test
	void unidadesValidanCodigoTipoEstadoYSeparacionInstitucional() throws Exception {
		migrar();
		nuevaUnidad(institucion, "U01");
		rechaza("23505", () -> nuevaUnidad(institucion, "U01"));
		nuevaUnidad(nuevaInstitucion(), "U01");
		rechaza("23514", () -> nuevaUnidad(institucion, "  "));
		rechaza("23514", () -> consultas.update("UPDATE planning_unit SET type_planning_unit = 'unknown'"));
		rechaza("23514", () -> consultas.update("UPDATE planning_unit SET status_planning_unit = 'unknown'"));
	}

	@Test
	void recuperacionVaciaRestauraDefinicionesYFilasOriginales() throws Exception {
		String originales = filasOriginales();
		String definicion = definiciones();
		migrar();
		ejecutarArchivo("U001__retirar_nucleo_mensual.sql");
		assertEquals(originales, filasOriginales());
		assertEquals(definicion, definiciones());
	}

	@Test
	void recuperacionSeNiegaABorrarUnidadesAunqueNoTenganPlanes() throws Exception {
		migrar();
		nuevaUnidad(institucion, "U01");
		rechaza("55000", () -> ejecutarRecuperacion());
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM planning_unit", Integer.class));
	}

	@Test
	void recuperacionSeNiegaABorrarPlanesInclusoCancelados() throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		planMensual(unidad, inicio, inicio.withDayOfMonth(30), 1, "cancelled");
		rechaza("55000", () -> ejecutarRecuperacion());
		assertEquals(1, consultas.queryForObject("SELECT count(*) FROM schedule WHERE period_type_schedule = 'monthly'", Integer.class));
	}

	@Test
	void turnosMensualesConservanExclusionesGlobalesConHorariosSemanales() throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		UUID plan = planMensual(unidad, inicio, inicio.withDayOfMonth(30), 1, "draft");
		rechaza("23P01", () -> nuevoTurno(plan, "approved"));
		UUID turno = nuevoTurno(plan, "draft");
		RepositorioProgramacion repositorio = new RepositorioProgramacion(consultas);
		assertEquals(2, repositorio.futuros(institucion, profesional).size());
		assertEquals(1, repositorio.aprobadosEn(institucion,
				inicio.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
				inicio.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), plan).size());
		assertThrows(CatalogoException.class, () -> repositorio.buscarTurno(institucion, turno));
	}

	@Test
	void repositorioSemanalFuncionaAntesYDespuesDeMigrar() throws Exception {
		RepositorioProgramacion repositorio = new RepositorioProgramacion(consultas);
		assertEquals(legado, repositorio.buscarHorario(institucion, legado).id());
		assertEquals(1, repositorio.turnos(institucion, legado).size());
		migrar();
		assertEquals(legado, repositorio.operativo(institucion, inicio).orElseThrow().id());
		assertEquals(legado, repositorio.aprobado(institucion, inicio).orElseThrow().id());
		assertEquals(1, repositorio.turnos(institucion, legado).size());
		assertEquals(2, repositorio.crearHorario(institucion, actor, inicio).version());
	}

	@Test
	void operacionesSemanalesPorIdNoPuedenLeerNiModificarPlanMensual() throws Exception {
		migrar();
		UUID unidad = nuevaUnidad(institucion, "U01");
		UUID plan = planMensual(unidad, inicio, inicio.withDayOfMonth(30), 7, "draft");
		UUID identificador = nuevoTurno(plan, "draft");
		RepositorioProgramacion repositorio = new RepositorioProgramacion(consultas);
		Turno turno = new Turno(identificador, plan, profesional, consultorio, especialidad,
				inicio.atTime(13, 0).toInstant(java.time.ZoneOffset.UTC),
				inicio.atTime(15, 0).toInstant(java.time.ZoneOffset.UTC), "draft", "Ensayo", "", "", "");
		assertThrows(CatalogoException.class, () -> repositorio.buscarHorario(institucion, plan));
		assertThrows(CatalogoException.class, () -> repositorio.buscarTurno(institucion, identificador));
		assertThrows(CatalogoException.class, () -> repositorio.aprobar(institucion, plan, actor));
		assertThrows(CatalogoException.class, () -> repositorio.cambiarEstado(institucion, plan, "cancelled"));
		assertThrows(CatalogoException.class, () -> repositorio.insertarTurno(institucion, turno));
		assertThrows(CatalogoException.class, () -> repositorio.actualizarTurno(institucion, turno));
		assertThrows(CatalogoException.class, () -> repositorio.eliminarTurno(institucion, identificador));
		assertThrows(CatalogoException.class, () -> repositorio.verificarSinEjecucion(institucion, plan));
		assertThrows(CatalogoException.class, () -> repositorio.copiarContenido(institucion, plan, legado));
		assertThrows(CatalogoException.class, () -> repositorio.copiarContenido(institucion, legado, plan));
		assertTrue(repositorio.turnos(institucion, plan).isEmpty());
		assertEquals("draft", consultas.queryForObject("SELECT status_schedule FROM schedule WHERE id_schedule = ?", String.class, plan));
		assertEquals("draft", consultas.queryForObject("SELECT status_shift FROM shift WHERE id_shift = ?", String.class, identificador));
	}

	private void ejecutarRecuperacion() {
		try { ejecutarArchivo("U001__retirar_nucleo_mensual.sql"); }
		catch (java.io.IOException fallo) { throw new IllegalStateException(fallo); }
		catch (RuntimeException fallo) { throw fallo; }
		catch (Exception fallo) { throw new IllegalStateException(fallo); }
	}
}
