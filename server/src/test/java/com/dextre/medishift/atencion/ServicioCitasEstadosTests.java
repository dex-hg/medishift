package com.dextre.medishift.atencion;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.DatosAtencion.Cita;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.programacion.RepositorioProgramacion;
import com.dextre.medishift.programacion.RepositorioRestriccionesProgramacion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioCitasEstadosTests {
    private final UUID institucion = UUID.randomUUID(), actor = UUID.randomUUID(), id = UUID.randomUUID();
    private RepositorioCitas citas;
    private ServicioCitas servicio;
    @BeforeEach
    void preparar() {
        citas = mock(RepositorioCitas.class);
        var acceso = mock(ServicioAccesoMensual.class);
        when(acceso.consultarProgramacion(institucion, actor)).thenReturn(new ServicioAccesoMensual.PermisosProgramacion(true, null, true));
        servicio = new ServicioCitas(acceso, citas, mock(RepositorioPacientes.class), mock(ServicioPacientes.class),
                mock(RepositorioEspecialidades.class), mock(RepositorioProgramacion.class), mock(RepositorioRestriccionesProgramacion.class));
    }
    private Cita cita(Instant inicio, Instant fin, String estado) {
        return new Cita(id, UUID.randomUUID(), "Paciente", "PAC-ENSAYO", UUID.randomUUID(), UUID.randomUUID(), "Doctor", UUID.randomUUID(), "Sala", inicio, fin, estado, "revision");
    }
    @Test
    void permiteAtendidaAlComenzarYNoAsistioTrasFinalizar() {
        Instant ahora = Instant.now();
        when(citas.buscar(institucion, id)).thenReturn(cita(ahora.minusSeconds(60), ahora.plusSeconds(60), "programada"));
        servicio.cambiarEstado(institucion, actor, id, "revision", "atendida");
        verify(citas).cambiarEstado(institucion, id, "atendida");
        when(citas.buscar(institucion, id)).thenReturn(cita(ahora.minusSeconds(120), ahora.minusSeconds(60), "programada"));
        servicio.cambiarEstado(institucion, actor, id, "revision", "no_asistio");
        verify(citas).cambiarEstado(institucion, id, "no_asistio");
    }
    @Test
    void rechazaAtendidaFuturaInasistenciaAntesDelFinYEstadoFinal() {
        Instant ahora = Instant.now();
        when(citas.buscar(institucion, id)).thenReturn(cita(ahora.plusSeconds(60), ahora.plusSeconds(120), "programada"));
        assertThrows(CatalogoException.class, () -> servicio.cambiarEstado(institucion, actor, id, "revision", "atendida"));
        assertThrows(CatalogoException.class, () -> servicio.cambiarEstado(institucion, actor, id, "revision", "no_asistio"));
        when(citas.buscar(institucion, id)).thenReturn(cita(ahora.minusSeconds(120), ahora.minusSeconds(60), "atendida"));
        assertThrows(CatalogoException.class, () -> servicio.cambiarEstado(institucion, actor, id, "revision", "cancelada"));
        verify(citas, never()).cambiarEstado(any(), any(), any());
    }
}
