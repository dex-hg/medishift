package com.dextre.medishift.feriados;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class DatosFeriados {

	private DatosFeriados() { }

	public enum Pais {
		PERU("PE", "Perú", "America/Lima"), COLOMBIA("CO", "Colombia", "America/Bogota"),
		CHILE("CL", "Chile", "America/Santiago"), BOLIVIA("BO", "Bolivia", "America/La_Paz"),
		MEXICO("MX", "México", "America/Mexico_City"), ARGENTINA("AR", "Argentina", "America/Argentina/Buenos_Aires");

		private final String codigo;
		private final String nombre;
		private final String zona;

		Pais(String codigo, String nombre, String zona) { this.codigo = codigo; this.nombre = nombre; this.zona = zona; }
		public String codigo() { return codigo; }
		public String nombre() { return nombre; }
		public String zona() { return zona; }
		public static Optional<Pais> deZona(String zona) {
			return Arrays.stream(values()).filter(pais -> pais.zona.equals(zona)).findFirst();
		}
	}

	public record Feriado(LocalDate fecha, String nombre) { }
	public record CalendarioAnual(String codigoPais, int anio, Instant actualizado, List<Feriado> dias) { }
	public record CalendarioFeriados(String codigoPais, String pais, String estado, String fuente, Instant actualizado,
			int minutosPorFeriado, boolean requiereActualizacion, List<Feriado> dias, String mensaje) { }
}
