package com.dextre.medishift.atencion;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.DatosAtencion.IdentidadDni;
import com.dextre.medishift.seguridad.LimitadorSolicitudes;
import com.dextre.medishift.seguridad.LimitadorSolicitudes.Operacion;

@Service
public class ServicioIdentidadDni {
    private final ServicioAccesoMensual acceso;
    private final ClienteIdentidadDni proveedor;
    private final LimitadorSolicitudes limites;

    public ServicioIdentidadDni(ServicioAccesoMensual acceso, ClienteIdentidadDni proveedor, LimitadorSolicitudes limites) {
        this.acceso = acceso;
        this.proveedor = proveedor;
        this.limites = limites;
    }

    /** La consulta de permisos termina antes de abrir la conexión con el proveedor. */
    public IdentidadDni consultar(UUID institucion, UUID actor, String dni) {
        acceso.consultarProgramacion(institucion, actor);
        String documento = ValidadorAtencion.dni(dni);
        limites.reservar(Operacion.CONSULTA_DNI, null, actor.toString());
        return proveedor.consultar(documento);
    }
}
