package com.dextre.medishift.cuenta;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.medishift.acceso.ServicioAccesoMensual;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.RepositorioEspecialidades;
import com.dextre.medishift.cuenta.DatosConfiguracion.Claves;
import com.dextre.medishift.cuenta.DatosConfiguracion.Cuenta;
import com.dextre.medishift.cuenta.DatosConfiguracion.Institucion;
import com.dextre.medishift.cuenta.DatosConfiguracion.Perfil;
import com.dextre.medishift.cuenta.DatosConfiguracion.Preferencias;
import com.dextre.medishift.programacion.DatosProgramacion.Turno;
import com.dextre.medishift.programacion.ReglasCargaProgramacion;
import com.dextre.medishift.programacion.TiempoProgramacion;

@Service
public class ServicioConfiguracion {

	private final RepositorioConfiguracion repositorio;
	private final ServicioAccesoMensual acceso;
	private final RepositorioEspecialidades exclusiones;
	private final ReglasCargaProgramacion carga;
	private final PasswordEncoder codificador;

	public ServicioConfiguracion(RepositorioConfiguracion repositorio, ServicioAccesoMensual acceso,
			RepositorioEspecialidades exclusiones,
			ReglasCargaProgramacion carga, PasswordEncoder codificador) {
		this.repositorio = repositorio;
		this.acceso = acceso;
		this.exclusiones = exclusiones;
		this.carga = carga;
		this.codificador = codificador;
	}

	@Transactional(readOnly = true)
	public Institucion institucion(UUID institucion) { return repositorio.institucion(institucion); }

	@Transactional(readOnly = true)
	public Cuenta cuenta(UUID institucion, UUID actor) { return repositorio.cuenta(institucion, actor); }

	@Transactional
	public Institucion guardarInstitucion(UUID institucion, UUID actor, String revision, Institucion datos) {
		exclusiones.bloquearInstitucion(institucion);
		acceso.exigirAdministrador(institucion, actor);
		Institucion actual = repositorio.institucion(institucion);
		comprobarRevision(revision, actual.revision());
		if (!actual.zonaHoraria().equals(datos.zonaHoraria()) && repositorio.tieneProgramacion(institucion)) {
			throw CatalogoException.conflicto("zonaHoraria", "La institución ya tiene programación. Conserva su zona horaria para proteger las fechas y el historial.");
		}
		if (datos.minutosDiarios() < actual.minutosDiarios() || datos.minutosSemanales() < actual.minutosSemanales()) {
			validarJornadasFuturas(institucion, datos);
		}
		String antes = repositorio.captura(institucion, null);
		repositorio.guardarInstitucion(institucion, datos);
		repositorio.auditar(institucion, actor, true, "update", antes, repositorio.captura(institucion, null));
		return repositorio.institucion(institucion);
	}

	private void validarJornadasFuturas(UUID institucion, Institucion datos) {
		ZoneId zona = ZoneId.of(datos.zonaHoraria());
		List<Turno> futuros = repositorio.jornadasDesde(institucion, TiempoProgramacion.inicioSemana(LocalDate.now(zona)).atStartOfDay(zona).toInstant());
		Set<LocalDate> semanas = futuros.stream().filter(turno -> turno.fin().isAfter(Instant.now())).map(turno -> TiempoProgramacion.inicioSemana(
				turno.inicio().atZone(zona).toLocalDate())).collect(Collectors.toSet());
		for (LocalDate lunes : semanas) {
			List<Turno> turnos = futuros.stream().filter(turno -> turno.inicio().isBefore(lunes.plusDays(7).atStartOfDay(zona).toInstant())
					&& turno.fin().isAfter(lunes.atStartOfDay(zona).toInstant())).toList();
			try { carga.validarHoras(turnos, List.of(), lunes, zona, datos.politica()); }
			catch (CatalogoException excepcion) {
				throw CatalogoException.conflicto("minutosDiarios", "Hay turnos futuros que exceden estos límites. "
						+ excepcion.getMessage() + " Ajusta primero la programación.");
			}
		}
	}

	@Transactional
	public Cuenta guardarPerfil(UUID institucion, UUID actor, String revision, Perfil datos) {
		Cuenta actual = bloquearCuenta(institucion, actor, revision);
		if (actual.nombre().equals(datos.nombre())) return actual;
		String antes = repositorio.captura(institucion, actor);
		repositorio.guardarPerfil(institucion, actor, datos.nombre());
		repositorio.auditar(institucion, actor, false, "update_profile", antes, repositorio.captura(institucion, actor));
		return repositorio.cuenta(institucion, actor);
	}

	@Transactional
	public Cuenta guardarPreferencias(UUID institucion, UUID actor, String revision, Preferencias datos) {
		exclusiones.bloquearInstitucion(institucion);
		Cuenta actual = bloquearCuenta(institucion, actor, revision);
		long duracion = Duration.between(LocalTime.parse(datos.horaInicio()), LocalTime.parse(datos.horaFin())).toMinutes();
		boolean cambiaHoras = !actual.preferencias().horaInicio().equals(datos.horaInicio())
				|| !actual.preferencias().horaFin().equals(datos.horaFin());
		if (cambiaHoras && duracion > repositorio.institucion(institucion).minutosDiarios()) {
			throw CatalogoException.conflicto("horaFin", "El horario predeterminado excede el máximo diario de la institución.");
		}
		if (actual.preferencias().equals(datos)) return actual;
		String antes = repositorio.captura(institucion, actor);
		repositorio.guardarPreferencias(institucion, actor, datos);
		repositorio.auditar(institucion, actor, false, "update_preferences", antes, repositorio.captura(institucion, actor));
		return repositorio.cuenta(institucion, actor);
	}

	@Transactional
	public Cuenta cambiarContrasena(UUID institucion, UUID actor, String revision, Claves datos) {
		bloquearCuenta(institucion, actor, revision);
		boolean coincide;
		try { coincide = codificador.matches(datos.contrasenaActual(), repositorio.hashContrasena(institucion, actor)); }
		catch (IllegalArgumentException excepcion) { coincide = false; }
		if (!coincide) throw CatalogoException.conflicto("contrasenaActual", "La contraseña actual no es correcta.");
		repositorio.guardarContrasena(institucion, actor, codificador.encode(datos.contrasenaNueva()));
		repositorio.auditar(institucion, actor, false, "change_password", null, "{\"contrasenaActualizada\":true}");
		return repositorio.cuenta(institucion, actor);
	}

	private Cuenta bloquearCuenta(UUID institucion, UUID actor, String revision) {
		repositorio.bloquearCuenta(institucion, actor);
		Cuenta actual = repositorio.cuenta(institucion, actor);
		comprobarRevision(revision, actual.revision());
		return actual;
	}

	private void comprobarRevision(String recibida, String actual) {
		if (!actual.equals(recibida)) throw CatalogoException.conflicto("revision", "La configuración cambió en otra ventana. Recarga antes de guardar.");
	}
}
