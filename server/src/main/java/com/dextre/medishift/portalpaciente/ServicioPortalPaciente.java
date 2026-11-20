package com.dextre.medishift.portalpaciente;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.CitaPortal;
import com.dextre.medishift.portalpaciente.RepositorioPortalPaciente.Hospital;
import com.dextre.medishift.programacion.mensual.PeriodoMensual;
import com.dextre.medishift.seguridad.LimitadorSolicitudes;
import com.dextre.medishift.seguridad.LimitadorSolicitudes.Operacion;
import com.dextre.medishift.sesion.AccesoNoAutorizadoException;
import com.dextre.medishift.sesion.IdentidadPortalPaciente;

@Service
public class ServicioPortalPaciente {
    private final RepositorioPortalPaciente portal;
    private final LimitadorSolicitudes limites;

    public record Calendario(String periodo, List<Hospital> hospitales, List<CitaPortal> citas) { }

    public ServicioPortalPaciente(RepositorioPortalPaciente portal, LimitadorSolicitudes limites) {
        this.portal = portal;
        this.limites = limites;
    }

    public void autenticar(IdentidadPortalPaciente identidad, String direccionRemota) {
        var intento = limites.reservar(Operacion.SESION_PACIENTE, direccionRemota, identidad.dni());
        boolean fichaActiva;
        try {
            fichaActiva = portal.existeFichaActiva(identidad);
        } catch (DataAccessException fallo) {
            limites.liberarIdentidad(intento);
            throw fallo;
        }
        if (!fichaActiva) {
            throw CatalogoException.autenticacionFallida(
                    "No se pudo verificar el DNI y la fecha de nacimiento. Revisa que coincidan con los datos registrados en el hospital.");
        }
        limites.liberarIdentidad(intento);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calendario calendario(IdentidadPortalPaciente identidad, String textoPeriodo, PeriodoMensual periodo) {
        List<Hospital> hospitales = portal.hospitales(identidad);
        if (hospitales.isEmpty()) throw new AccesoNoAutorizadoException();
        return new Calendario(textoPeriodo, hospitales, portal.citas(identidad, periodo));
    }

}
