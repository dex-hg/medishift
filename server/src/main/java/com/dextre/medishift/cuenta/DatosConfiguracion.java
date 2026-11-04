package com.dextre.medishift.cuenta;

import java.time.Instant;

public final class DatosConfiguracion {

	private DatosConfiguracion() { }

	public record Politica(int minutosDiarios, int minutosSemanales, int minutosPorFeriado) {
		public static Politica predeterminada() { return new Politica(360, 2160, 360); }
	}

	public record Institucion(String nombreInstitucion, String zonaHoraria, int minutosDiarios,
			int minutosSemanales, int minutosPorFeriado, boolean autoprogramacionMedica, String revision) {
		public Institucion(String nombre, String zona, int diarios, int semanales, int feriados, String revision) {
			this(nombre, zona, diarios, semanales, feriados, false, revision);
		}
		public Politica politica() { return new Politica(minutosDiarios, minutosSemanales, minutosPorFeriado); }
	}

	public record Preferencias(boolean barraCompacta, String horaInicio, String horaFin) { }

	public record Cuenta(String nombre, String correo, Instant creada, boolean esAdministrador,
			Preferencias preferencias, String revision) { }

	public record Perfil(String nombre) { }

	public record Claves(String contrasenaActual, String contrasenaNueva) {
		@Override public String toString() { return "Claves[datos omitidos]"; }
	}
}
