package com.dextre.medishift.acceso;

import java.util.UUID;
import com.dextre.medishift.programacion.RevisionesProgramacion;

public final class DatosAccesoDoctores {
	private DatosAccesoDoctores() { }

	public record AccesoDoctor(UUID idCuenta, String correo, boolean habilitado, boolean credencialesEditables, String revision) { }

	public record Vinculo(UUID idCuenta, String correo, String estadoCuenta, String estadoProfesional, boolean rolDoctor,
			boolean tieneOtrosRoles, String huellaClave) {
		public AccesoDoctor vista(UUID profesional) {
			boolean habilitado = rolDoctor && "active".equals(estadoCuenta) && "active".equals(estadoProfesional);
			return new AccesoDoctor(idCuenta, correo, habilitado, !tieneOtrosRoles, RevisionesProgramacion.resumir(
					profesional, idCuenta, correo, estadoCuenta, estadoProfesional, rolDoctor, tieneOtrosRoles, huellaClave));
		}
	}
}
