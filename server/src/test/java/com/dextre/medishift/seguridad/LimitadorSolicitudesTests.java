package com.dextre.medishift.seguridad;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.dextre.medishift.seguridad.LimitadorSolicitudes.LimiteAlcanzado;
import com.dextre.medishift.seguridad.LimitadorSolicitudes.Operacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitadorSolicitudesTests {
	private static final String DIRECCION = "192.0.2.1";
	private static final String IDENTIDAD = "identidad-de-prueba";

	@Test
	void venceLaVentanaYRedondeaLaEsperaAlSiguienteSegundo() {
		var tiempo = new AtomicLong();
		var limitador = new LimitadorSolicitudes(tiempo::get, 40);
		for (int intento = 0; intento < 8; intento++) {
			limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD);
		}
		tiempo.set(Duration.ofMillis(599_500).toNanos());
		var error = assertThrows(LimiteAlcanzado.class,
				() -> limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD));
		assertEquals(1, error.segundos());
		tiempo.set(Duration.ofMinutes(10).toNanos());
		assertDoesNotThrow(() -> limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD));
	}

	@Test
	void laDireccionLimitaInclusoCuandoCadaIdentidadEsDistinta() {
		var limitador = new LimitadorSolicitudes(() -> 0, 100);
		for (int intento = 0; intento < 60; intento++) {
			limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, "cuenta-" + intento);
		}
		assertThrows(LimiteAlcanzado.class,
				() -> limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, "otra-cuenta"));
		assertDoesNotThrow(() -> limitador.reservar(Operacion.SESION_PERSONAL, "192.0.2.2", "otra-cuenta"));
		assertDoesNotThrow(() -> limitador.reservar(Operacion.SESION_PACIENTE, DIRECCION, IDENTIDAD));
	}

	@Test
	void elCupoLlenoConservaLasVentanasActivasYElExitoSoloLiberaSuReserva() {
		var limitador = new LimitadorSolicitudes(() -> 0, 3);
		var exito = limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD);
		for (int fallo = 0; fallo < 7; fallo++) {
			limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD);
		}
		assertThrows(LimiteAlcanzado.class,
				() -> limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, "otra-cuenta"));
		limitador.liberarIdentidad(exito);
		limitador.liberarIdentidad(exito);
		limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD);
		assertThrows(LimiteAlcanzado.class,
				() -> limitador.reservar(Operacion.SESION_PERSONAL, DIRECCION, IDENTIDAD));
	}

	@Test
	void lasSolicitudesSimultaneasNoSuperanElCupoDeUnaIdentidad() throws Exception {
		var limitador = new LimitadorSolicitudes(() -> 0, 40);
		var salida = new CountDownLatch(1);
		try (var ejecutor = Executors.newFixedThreadPool(16)) {
			List<Future<Boolean>> resultados = new ArrayList<>();
			for (int intento = 0; intento < 16; intento++) {
				String direccion = "192.0.2." + (intento + 1);
				resultados.add(ejecutor.submit(() -> {
					assertTrue(salida.await(5, TimeUnit.SECONDS));
					try {
						limitador.reservar(Operacion.SESION_PACIENTE, direccion, IDENTIDAD);
						return true;
					} catch (LimiteAlcanzado fallo) {
						return false;
					}
				}));
			}
			salida.countDown();
			int aceptados = 0;
			for (var resultado : resultados) {
				if (resultado.get(5, TimeUnit.SECONDS)) aceptados++;
			}
			assertEquals(8, aceptados);
		}
	}
}
