package com.dextre.medishift.catalogos;

import java.util.List;
import java.util.UUID;

import com.dextre.medishift.programacion.RevisionesProgramacion;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DatosCatalogos {

	private DatosCatalogos() {
	}

	public record Profesional(UUID id, String nombres, String apellidos, String colegiatura,
			String correo, String telefono, String categoria, String estado, String especialidad, List<Especialidad> etiquetas) {
		public Profesional(UUID id, String nombres, String apellidos, String colegiatura, String correo,
				String telefono, String categoria, String estado, String especialidad) {
			this(id, nombres, apellidos, colegiatura, correo, telefono, categoria, estado, especialidad, List.of());
		}

		@JsonProperty("revision") public String revision() { return RevisionesProgramacion.resumir(this); }
	}

	public record Consultorio(UUID id, String codigo, String nombre, String ubicacion,
			String especialidad, String estado, boolean usoGeneral, List<Especialidad> etiquetas, AreaConsultorio area) {
		public Consultorio(UUID id, String codigo, String nombre, String ubicacion, String especialidad, String estado,
				boolean usoGeneral, List<Especialidad> etiquetas) {
			this(id, codigo, nombre, ubicacion, especialidad, estado, usoGeneral, etiquetas, null);
		}

		public Consultorio(UUID id, String codigo, String nombre, String ubicacion, String especialidad, String estado) {
			this(id, codigo, nombre, ubicacion, especialidad, estado, especialidad.isEmpty(), List.of());
		}

		@JsonProperty("revision") public String revision() { return RevisionesProgramacion.resumir(this); }
	}

	public record AreaConsultorio(UUID id, String nombre, UUID idPadre, String nombrePadre, List<Especialidad> etiquetas) { }

	public record Especialidad(UUID id, String nombre) {
	}

	public record Etiqueta(UUID id, String nombre, boolean activa) {
		@JsonProperty("revision") public String revision() { return RevisionesProgramacion.resumir(this); }
	}

	public record CambioEtiqueta(String nombre, boolean activa) { }

}
