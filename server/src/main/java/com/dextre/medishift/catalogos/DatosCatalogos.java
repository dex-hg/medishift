package com.dextre.medishift.catalogos;

import java.util.UUID;

public final class DatosCatalogos {

	private DatosCatalogos() {
	}

	public record Profesional(UUID id, String nombres, String apellidos, String colegiatura,
			String correo, String telefono, String categoria, String estado, String especialidad) {
	}

	public record Consultorio(UUID id, String codigo, String nombre, String ubicacion,
			String especialidad, String estado) {
	}

	public record Especialidad(UUID id, String nombre) {
	}

}
