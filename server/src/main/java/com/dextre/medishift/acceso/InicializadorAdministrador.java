package com.dextre.medishift.acceso;

import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "medishift.inicializar-administrador", havingValue = "true")
public class InicializadorAdministrador implements ApplicationRunner {

	private final ServicioAccesoMensual accesos;
	private final ConfigurableApplicationContext contexto;
	private final Environment configuracion;

	public InicializadorAdministrador(ServicioAccesoMensual accesos,
			ConfigurableApplicationContext contexto, Environment configuracion) {
		this.accesos = accesos;
		this.contexto = contexto;
		this.configuracion = configuracion;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		try {
			if (contexto instanceof WebApplicationContext) {
				throw new IllegalStateException(
						"La inicialización del administrador requiere spring.main.web-application-type=none.");
			}
			UUID institucion = identificador("medishift.admin.institucion");
			UUID cuenta = identificador("medishift.admin.cuenta");
			accesos.inicializarAdministrador(institucion, cuenta);
		} finally {
			// El comando no debe dejar el proceso ni la conexión de datos activos.
			contexto.close();
		}
	}

	private UUID identificador(String propiedad) {
		String valor = configuracion.getProperty(propiedad);
		if (valor != null) {
			try {
				UUID identificador = UUID.fromString(valor);
				if (identificador.toString().equalsIgnoreCase(valor)) {
					return identificador;
				}
			} catch (IllegalArgumentException fallo) {
				// No incluir el contenido de la propiedad en diagnósticos.
			}
		}
		throw new IllegalStateException("Debes indicar un UUID completo en " + propiedad + ".");
	}
}
