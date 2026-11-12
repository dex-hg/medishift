package com.dextre.medishift.portalpaciente;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.CitaPortal;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.Hospital;
import com.dextre.medishift.programacion.mensual.PeriodoMensual;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.IdentidadPortalPaciente;

@Service
public class ServicioPortalPaciente {
    private static final long VENTANA_INTENTOS = Duration.ofMinutes(10).toNanos();
    private static final int INTENTOS_POR_DNI = 8;
    private static final int INTENTOS_POR_ORIGEN = 60;
    private static final int MAXIMO_CLAVES_CONTROLADAS = 10_000;

    private final RepositorioPortalPaciente portal;
    private final LinkedHashMap<String, Intentos> intentos = new LinkedHashMap<>(128, 0.75f, true);

    public record Calendario(String periodo, List<Hospital> hospitales, List<CitaPortal> citas) { }

    private record Intentos(long inicio, int cantidad) { }

    public ServicioPortalPaciente(RepositorioPortalPaciente portal) {
        this.portal = portal;
    }

    public void autenticar(IdentidadPortalPaciente identidad, String direccionRemota) {
        String documento = "dni:" + identidad.dni();
        String origen = "origen:" + direccionRemota;
        long inicioOrigen = registrarIntento(documento, origen);
        if (!portal.existeFichaActiva(identidad)) {
            throw CatalogoException.autenticacionFallida(
                    "No se pudo verificar el DNI y la fecha de nacimiento. Revisa que coincidan con los datos registrados en el hospital.");
        }
        confirmarIngreso(documento, origen, inicioOrigen);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calendario calendario(IdentidadPortalPaciente identidad, String textoPeriodo, PeriodoMensual periodo) {
        List<Hospital> hospitales = portal.hospitales(identidad);
        if (hospitales.isEmpty()) throw new AccesoNoAutorizadoException();
        return new Calendario(textoPeriodo, hospitales, portal.citas(identidad, periodo));
    }

    private synchronized long registrarIntento(String documento, String origen) {
        long ahora = System.nanoTime();
        Intentos porDni = ventana(documento, ahora);
        Intentos porOrigen = ventana(origen, ahora);
        if (porDni.cantidad() >= INTENTOS_POR_DNI || porOrigen.cantidad() >= INTENTOS_POR_ORIGEN) {
            throw CatalogoException.limiteIntentos("Se alcanzó el límite de intentos. Inténtalo en unos minutos.");
        }
        contar(documento, porDni);
        contar(origen, porOrigen);
        return porOrigen.inicio();
    }

    private Intentos ventana(String clave, long ahora) {
        Intentos actual = intentos.get(clave);
        return actual == null || ahora - actual.inicio() >= VENTANA_INTENTOS ? new Intentos(ahora, 0) : actual;
    }

    private void contar(String clave, Intentos ventana) {
        intentos.put(clave, new Intentos(ventana.inicio(), ventana.cantidad() + 1));
        if (intentos.size() > MAXIMO_CLAVES_CONTROLADAS) intentos.pollFirstEntry();
    }

    private synchronized void confirmarIngreso(String documento, String origen, long inicioOrigen) {
        intentos.remove(documento);
        // Los ingresos correctos no consumen el cupo compartido por un mismo origen.
        Intentos actual = intentos.get(origen);
        if (actual != null && actual.inicio() == inicioOrigen) {
            if (actual.cantidad() == 1) intentos.remove(origen);
            else intentos.put(origen, new Intentos(inicioOrigen, actual.cantidad() - 1));
        }
    }
}
