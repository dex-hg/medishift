package com.dextre.medishift.atencion;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.atencion.DatosAtencion.IdentidadDni;

@Service
public class ServicioIdentidadDni {
    private final ServicioAccesoMensual acceso;
    private final ClienteIdentidadDni proveedor;

    public ServicioIdentidadDni(ServicioAccesoMensual acceso, ClienteIdentidadDni proveedor) {
        this.acceso = acceso;
        this.proveedor = proveedor;
    }

    /** La consulta de permisos termina antes de abrir la conexión con el proveedor. */
    public IdentidadDni consultar(UUID institucion, UUID actor, String dni) {
        acceso.consultarProgramacion(institucion, actor);
        return proveedor.consultar(ValidadorAtencion.dni(dni));
    }
}
