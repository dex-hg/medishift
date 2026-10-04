package com.dextre.medishift.disponibilidad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.DatosCatalogos.Profesional;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.catalogos.RepositorioProfesionales;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.Disponibilidad;
import com.dextre.medishift.disponibilidad.DatosDisponibilidades.SolicitudDisponibilidad;
import com.dextre.medishift.programacion.ReglasProgramacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServicioDisponibilidadesTests {

	private RepositorioDisponibilidades disponibilidades;
	private RepositorioProfesionales profesionales;
	private RepositorioEspecialidades especialidades;
	private ReglasProgramacion reglas;
	private ServicioDisponibilidades servicio;
	private UUID institucion;
	private UUID identificador;
	private UUID profesional;
	private Disponibilidad actual;

	@BeforeEach
	void prepararServicioYRegistroActual() {
		disponibilidades = mock(RepositorioDisponibilidades.class);
		profesionales = mock(RepositorioProfesionales.class);
		especialidades = mock(RepositorioEspecialidades.class);
		reglas = mock(ReglasProgramacion.class);
		servicio = new ServicioDisponibilidades(disponibilidades, profesionales, especialidades, reglas);
		institucion = UUID.randomUUID();
		identificador = UUID.randomUUID();
		profesional = UUID.randomUUID();
		actual = new Disponibilidad(identificador, profesional, "Ana Pérez", 1, "08:00", "16:00",
				"2027-01-04", "2027-01-31", "Activo", "", "a".repeat(64));
		when(disponibilidades.buscar(institucion, identificador)).thenReturn(Optional.of(actual));
		when(profesionales.buscar(institucion, profesional)).thenReturn(Optional.of(profesional(profesional, "Activo")));
	}

	@Test
	void creacionSerializaInstitucionValidaProfesionalYRevalidaTurnosDespuesDeInsertar() {
		when(disponibilidades.buscar(eq(institucion), any())).thenReturn(Optional.of(actual));
		SolicitudDisponibilidad solicitud = solicitud(profesional);
		assertEquals(actual, servicio.crear(institucion, solicitud));
		InOrder orden = inOrder(especialidades, profesionales, disponibilidades, reglas);
		orden.verify(especialidades).bloquearInstitucion(institucion);
		orden.verify(profesionales).buscar(institucion, profesional);
		orden.verify(disponibilidades).verificarCruces(eq(institucion), any(), eq(solicitud));
		orden.verify(disponibilidades).insertar(eq(institucion), any(), eq(solicitud));
		orden.verify(reglas).validarCambioDisponibilidades(institucion, profesional);
		orden.verify(disponibilidades).buscar(eq(institucion), any());
	}

	@Test
	void actualizacionSerializaLecturaComparaRevisionYValidaAntesYDespuesDeEscribir() {
		SolicitudDisponibilidad solicitud = solicitud(profesional);
		assertEquals(actual, servicio.actualizar(institucion, identificador, actual.revision(), solicitud));
		InOrder orden = inOrder(especialidades, profesionales, disponibilidades, reglas);
		orden.verify(especialidades).bloquearInstitucion(institucion);
		orden.verify(disponibilidades).buscar(institucion, identificador);
		orden.verify(profesionales).buscar(institucion, profesional);
		orden.verify(disponibilidades).verificarCruces(institucion, identificador, solicitud);
		orden.verify(disponibilidades).actualizar(institucion, identificador, solicitud);
		orden.verify(reglas).validarCambioDisponibilidades(institucion, profesional);
		orden.verify(disponibilidades).buscar(institucion, identificador);
	}

	@Test
	void moverVentanaRevalidaLosDosProfesionalesBajoElMismoBloqueo() {
		UUID nuevo = UUID.randomUUID();
		when(profesionales.buscar(institucion, nuevo)).thenReturn(Optional.of(profesional(nuevo, "Activo")));
		servicio.actualizar(institucion, identificador, actual.revision(), solicitud(nuevo));
		InOrder orden = inOrder(disponibilidades, reglas);
		orden.verify(disponibilidades).actualizar(eq(institucion), eq(identificador), any());
		orden.verify(reglas).validarCambioDisponibilidades(institucion, profesional);
		orden.verify(reglas).validarCambioDisponibilidades(institucion, nuevo);
	}

	@Test
	void borrarRevalidaTurnosDespuesDeEliminarYPermiteProfesionalYaInactivo() {
		servicio.eliminar(institucion, identificador, actual.revision());
		InOrder orden = inOrder(especialidades, disponibilidades, reglas);
		orden.verify(especialidades).bloquearInstitucion(institucion);
		orden.verify(disponibilidades).buscar(institucion, identificador);
		orden.verify(disponibilidades).eliminar(institucion, identificador);
		orden.verify(reglas).validarCambioDisponibilidades(institucion, profesional);
		verifyNoInteractions(profesionales);
	}

	@Test
	void revisionObsoletaImpideEditarYEliminarSinLlegarAlProfesionalNiGuardas() {
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> servicio.actualizar(institucion, identificador, "b".repeat(64), solicitud(profesional)))
				.obtenerEstado().value());
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> servicio.eliminar(institucion, identificador, "b".repeat(64))).obtenerEstado().value());
		verifyNoInteractions(profesionales, reglas);
		verify(disponibilidades, never()).actualizar(any(), any(), any());
		verify(disponibilidades, never()).eliminar(any(), any());
	}

	@Test
	void profesionalAjenoOInexistenteDevuelve404SinEscribir() {
		when(profesionales.buscar(institucion, profesional)).thenReturn(Optional.empty());
		assertEquals(404, assertThrows(CatalogoException.class, () -> servicio.crear(institucion, solicitud(profesional)))
				.obtenerEstado().value());
		verifyNoInteractions(disponibilidades, reglas);
	}

	@Test
	void profesionalInactivoImpideCrearYEditar() {
		when(profesionales.buscar(institucion, profesional)).thenReturn(Optional.of(profesional(profesional, "Inactivo")));
		assertEquals(409, assertThrows(CatalogoException.class, () -> servicio.crear(institucion, solicitud(profesional)))
				.obtenerEstado().value());
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> servicio.actualizar(institucion, identificador, actual.revision(), solicitud(profesional)))
				.obtenerEstado().value());
		verify(disponibilidades, never()).insertar(any(), any(), any());
		verify(disponibilidades, never()).actualizar(any(), any(), any());
		verifyNoInteractions(reglas);
	}

	@Test
	void guardasPropaganConflictoParaQueLaTransaccionReviertaLaMutacion() {
		doThrow(CatalogoException.conflicto("disponibilidad", "Un turno futuro perdería su cobertura."))
				.when(reglas).validarCambioDisponibilidades(institucion, profesional);
		assertEquals(409, assertThrows(CatalogoException.class,
				() -> servicio.eliminar(institucion, identificador, actual.revision())).obtenerEstado().value());
		verify(disponibilidades).eliminar(institucion, identificador);
	}

	@Test
	void busquedasYListasSoloConsultanLaInstitucionSolicitada() {
		when(disponibilidades.listar(institucion)).thenReturn(List.of(actual));
		assertEquals(List.of(actual), servicio.listar(institucion));
		UUID ajena = UUID.randomUUID();
		when(disponibilidades.buscar(ajena, identificador)).thenReturn(Optional.empty());
		assertEquals(404, assertThrows(CatalogoException.class, () -> servicio.buscar(ajena, identificador))
				.obtenerEstado().value());
		verifyNoInteractions(profesionales, especialidades, reglas);
	}

	private Profesional profesional(UUID id, String estado) {
		return new Profesional(id, "Ana", "Pérez", "CMP-123", "ana@medishift.test", "", "Médico", estado, "Medicina");
	}

	private SolicitudDisponibilidad solicitud(UUID profesional) {
		return new SolicitudDisponibilidad(profesional, 1, LocalTime.of(8, 0), LocalTime.of(16, 0),
				LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31), "Activo", "");
	}

}
