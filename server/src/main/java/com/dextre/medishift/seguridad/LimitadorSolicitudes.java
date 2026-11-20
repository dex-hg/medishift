package com.dextre.medishift.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Component;

@Component
public class LimitadorSolicitudes {

	public enum Operacion {
		SESION_PERSONAL(8, 60, 400, Duration.ofMinutes(10)),
		SESION_PACIENTE(8, 60, 400, Duration.ofMinutes(10)),
		REGISTRO(0, 20, 80, Duration.ofMinutes(10)),
		CONSULTA_DNI(15, 0, 120, Duration.ofMinutes(1));

		private final int porIdentidad;
		private final int porDireccion;
		private final int general;
		private final long duracionNanos;

		Operacion(int porIdentidad, int porDireccion, int general, Duration duracion) {
			this.porIdentidad = porIdentidad;
			this.porDireccion = porDireccion;
			this.general = general;
			this.duracionNanos = duracion.toNanos();
		}
	}

	private static final int MAXIMO_CONTADORES_POR_OPERACION = 4096;
	private static final long NANOS_POR_SEGUNDO = Duration.ofSeconds(1).toNanos();

	private final EnumMap<Operacion, LinkedHashMap<String, Ventana>> ventanas = new EnumMap<>(Operacion.class);
	private final byte[] sal = new byte[32];
	private final LongSupplier reloj;
	private final int capacidad;

	public LimitadorSolicitudes() {
		this(System::nanoTime, MAXIMO_CONTADORES_POR_OPERACION);
	}

	LimitadorSolicitudes(LongSupplier reloj, int capacidad) {
		if (capacidad < 3) {
			throw new IllegalArgumentException("La capacidad debe admitir los tres contadores de una solicitud.");
		}
		this.reloj = reloj;
		this.capacidad = capacidad;
		new SecureRandom().nextBytes(sal);
		for (Operacion operacion : Operacion.values()) {
			ventanas.put(operacion, new LinkedHashMap<>());
		}
	}

	/**
	 * Reserva todos los cupos juntos. Cada autenticación en curso ocupa un cupo
	 * hasta saber su resultado, incluso cuando llegan varias en paralelo.
	 */
	public Intento reservar(Operacion operacion, String direccion, String identidad) {
		var cuotas = new ArrayList<Cuota>(3);
		cuotas.add(new Cuota("general", operacion.general));
		if (operacion.porDireccion > 0) {
			String origen = direccion == null || direccion.isBlank() ? "desconocida" : direccion;
			cuotas.add(new Cuota("direccion:" + huella(origen), operacion.porDireccion));
		}
		String claveIdentidad = null;
		if (operacion.porIdentidad > 0) {
			if (identidad == null || identidad.isBlank()) {
				throw new IllegalArgumentException("La operación requiere una identidad validada.");
			}
			claveIdentidad = "identidad:" + huella(identidad);
			cuotas.add(new Cuota(claveIdentidad, operacion.porIdentidad));
		}
		return contar(operacion, cuotas, claveIdentidad);
	}

	private synchronized Intento contar(Operacion operacion, List<Cuota> cuotas, String claveIdentidad) {
		long ahora = reloj.getAsLong();
		long duracion = operacion.duracionNanos;
		var contadores = ventanas.get(operacion);
		while (!contadores.isEmpty() && contadores.firstEntry().getValue().restante(ahora, duracion) <= 0) {
			contadores.pollFirstEntry();
		}
		long espera = 0;
		int nuevas = 0;
		for (Cuota cuota : cuotas) {
			Ventana actual = contadores.get(cuota.clave());
			if (actual == null) {
				nuevas++;
			} else if (actual.total >= cuota.maximo()) {
				espera = Math.max(espera, actual.restante(ahora, duracion));
			}
		}
		// Una entrada nueva nunca desaloja una ventana activa.
		if (nuevas > 0 && contadores.size() + nuevas > capacidad) {
			espera = Math.max(espera, contadores.firstEntry().getValue().restante(ahora, duracion));
		}
		if (espera > 0) {
			throw new LimiteAlcanzado((espera + NANOS_POR_SEGUNDO - 1) / NANOS_POR_SEGUNDO);
		}
		for (Cuota cuota : cuotas) {
			contadores.computeIfAbsent(cuota.clave(), clave -> new Ventana(ahora)).total++;
		}
		return new Intento(contadores.get(claveIdentidad));
	}

	/** Libera solamente el cupo de identidad reservado para esta autenticación. */
	public synchronized void liberarIdentidad(Intento intento) {
		if (intento.identidad != null) {
			intento.identidad.total--;
			intento.identidad = null;
		}
	}

	private String huella(String valor) {
		try {
			MessageDigest criptografia = MessageDigest.getInstance("SHA-256");
			criptografia.update(sal);
			return HexFormat.of().formatHex(criptografia.digest(valor.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException fallo) {
			throw new IllegalStateException("SHA-256 debe estar disponible.", fallo);
		}
	}

	public static final class Intento {
		private Ventana identidad;

		private Intento(Ventana identidad) {
			this.identidad = identidad;
		}
	}

	static final class LimiteAlcanzado extends RuntimeException {
		private final long segundos;

		LimiteAlcanzado(long segundos) {
			this.segundos = segundos;
		}

		long segundos() {
			return segundos;
		}
	}

	private record Cuota(String clave, int maximo) { }

	private static final class Ventana {
		private final long inicio;
		private int total;

		Ventana(long inicio) {
			this.inicio = inicio;
		}

		long restante(long ahora, long duracion) {
			return duracion - (ahora - inicio);
		}
	}
}
