package com.dextre.medishift.sesion;

import java.time.LocalDate;

public record IdentidadPortalPaciente(String dni, LocalDate fechaNacimiento) {
    @Override
    public String toString() {
        return "IdentidadPortalPaciente[datos omitidos]";
    }
}
