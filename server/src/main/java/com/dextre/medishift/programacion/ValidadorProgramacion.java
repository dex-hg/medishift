package com.dextre.medishift.programacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.catalogos.ValidadorCatalogos;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudSemana;
import com.dextre.medishift.programacion.DatosProgramacion.SolicitudTurno;

public final class ValidadorProgramacion {

	private static final Set<String> CAMPOS_TURNO = Set.of("idProfesional", "idConsultorio", "idEspecialidad",
			"fecha", "horaInicio", "horaFin", "observacion");
	private static final Set<String> CAMPOS_SEMANA = Set.of("fechaInicio", "idHorario", "revision");

	private ValidadorProgramacion() { }

	public static SolicitudTurno validarTurno(Map<String, Object> solicitud) {
		Map<String, String> errores = iniciar(solicitud, CAMPOS_TURNO);
		UUID profesional = identificador(solicitud, "idProfesional", errores);
		UUID consultorio = identificador(solicitud, "idConsultorio", errores);
		UUID especialidad = identificador(solicitud, "idEspecialidad", errores);
		LocalDate fecha = fecha(solicitud, "fecha", errores);
		LocalTime inicio = hora(solicitud, "horaInicio", errores);
		LocalTime fin = hora(solicitud, "horaFin", errores);
		String observacion = texto(solicitud, "observacion", false, errores);
		if (observacion.codePointCount(0, observacion.length()) > 180) {
			errores.put("observacion", "La observación admite hasta 180 caracteres.");
		}
		if (inicio != null && fin != null && !fin.isAfter(inicio)) {
			errores.put("horaFin", "La hora final debe ser posterior. Los turnos nocturnos aún no están habilitados.");
		}
		lanzar(errores);
		return new SolicitudTurno(profesional, consultorio, especialidad, fecha, inicio, fin, observacion);
	}

	public static SolicitudSemana validarSemana(Map<String, Object> solicitud) {
		Map<String, String> errores = iniciar(solicitud, CAMPOS_SEMANA);
		LocalDate inicio = fecha(solicitud, "fechaInicio", errores);
		UUID horario = identificador(solicitud, "idHorario", errores);
		String revision = texto(solicitud, "revision", true, errores);
		if (!revision.matches("[a-f0-9]{64}")) {
			errores.put("revision", "Envía la revisión recibida al consultar la semana.");
		}
		if (inicio != null && inicio.getDayOfWeek() != DayOfWeek.MONDAY) {
			errores.put("fechaInicio", "La semana debe comenzar un lunes.");
		}
		if (inicio != null && inicio.plusDays(6).getYear() > 9999) {
			errores.put("fechaInicio", "Selecciona una semana completa dentro del año 9999.");
		}
		lanzar(errores);
		return new SolicitudSemana(inicio, horario, revision);
	}

	public static LocalDate validarInicio(String valor) {
		LocalDate fecha = TiempoProgramacion.analizarFecha(valor);
		if (fecha.getDayOfWeek() != DayOfWeek.MONDAY || fecha.plusDays(6).getYear() > 9999) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Selecciona un lunes para consultar una semana completa.",
					Map.of("fechaInicio", "La semana debe comenzar un lunes y contener siete fechas válidas."));
		}
		return fecha;
	}

	private static Map<String, String> iniciar(Map<String, Object> solicitud, Set<String> permitidos) {
		if (solicitud == null) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Envía un objeto JSON con los datos.", Map.of());
		}
		Map<String, String> errores = new LinkedHashMap<>();
		for (String campo : solicitud.keySet()) {
			if (!permitidos.contains(campo)) { errores.put(campo, "Este campo no forma parte del formulario."); }
		}
		return errores;
	}

	private static UUID identificador(Map<String, Object> solicitud, String campo, Map<String, String> errores) {
		String valor = texto(solicitud, campo, true, errores);
		try { return ValidadorCatalogos.validarId(valor); }
		catch (CatalogoException excepcion) { errores.put(campo, "Selecciona un identificador UUID completo."); return null; }
	}

	private static LocalDate fecha(Map<String, Object> solicitud, String campo, Map<String, String> errores) {
		String valor = texto(solicitud, campo, true, errores);
		try { return TiempoProgramacion.analizarFecha(valor); }
		catch (CatalogoException excepcion) { errores.put(campo, "Ingresa una fecha válida YYYY-MM-DD."); return null; }
	}

	private static LocalTime hora(Map<String, Object> solicitud, String campo, Map<String, String> errores) {
		String valor = texto(solicitud, campo, true, errores);
		try { return TiempoProgramacion.analizarHora(valor); }
		catch (CatalogoException excepcion) { errores.put(campo, "Ingresa una hora válida HH:mm."); return null; }
	}

	private static String texto(Map<String, Object> solicitud, String campo, boolean obligatorio,
			Map<String, String> errores) {
		if (!solicitud.containsKey(campo) && !obligatorio) { return ""; }
		Object valor = solicitud.get(campo);
		if (!(valor instanceof String contenido)) {
			errores.put(campo, "Este campo debe contener texto."); return "";
		}
		for (int indice = 0; indice < contenido.length(); indice++) {
			char caracter = contenido.charAt(indice);
			if (Character.isHighSurrogate(caracter)) {
				if (indice + 1 >= contenido.length() || !Character.isLowSurrogate(contenido.charAt(indice + 1))) {
					errores.put(campo, "El texto contiene una secuencia Unicode no válida."); break;
				}
				indice++;
			} else if (Character.isLowSurrogate(caracter) || Character.isISOControl(caracter)) {
				errores.put(campo, "El texto contiene un carácter no permitido."); break;
			}
		}
		String normalizado = ValidadorCatalogos.normalizarTexto(contenido);
		if (obligatorio && normalizado.isEmpty()) { errores.putIfAbsent(campo, "Completa este campo."); }
		if (!campo.equals("observacion") && !normalizado.equals(contenido)) {
			errores.put(campo, "Envía el valor sin espacios adicionales.");
		}
		return normalizado;
	}

	private static void lanzar(Map<String, String> errores) {
		if (!errores.isEmpty()) {
			throw new CatalogoException(HttpStatus.BAD_REQUEST, "Revisa los datos del formulario.", errores);
		}
	}

}
