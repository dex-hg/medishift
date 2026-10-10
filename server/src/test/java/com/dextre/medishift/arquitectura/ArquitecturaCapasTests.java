package com.dextre.medishift.arquitectura;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.dextre.medishift.MedishiftApplication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comprueba dependencias directas compiladas, sin iniciar Spring ni PostgreSQL. */
class ArquitecturaCapasTests {

	private static final Map<String, Class<?>> clases = new HashMap<>();
	private static final Map<Class<?>, Set<String>> dependencias = new HashMap<>();
	private static final int REFERENCIA_CLASE = 7;
	private static final int NOMBRE_Y_TIPO = 12;
	private static final int TIPO_METODO = 16;

	@BeforeAll
	static void cargarClasesDeProduccion() throws IOException, ClassNotFoundException {
		// El origen de la aplicación excluye target/test-classes y las dependencias externas.
		String origen = MedishiftApplication.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
		var recursos = new PathMatchingResourcePatternResolver()
				.getResources(origen + "com/dextre/medishift/**/*.class");
		assertTrue(recursos.length > 0, "No se encontraron clases de producción para verificar la arquitectura.");
		for (var recurso : recursos) {
			try (var entrada = recurso.getInputStream()) {
				ClassReader lector = new ClassReader(entrada);
				Class<?> clase = Class.forName(lector.getClassName().replace('/', '.'), false,
						MedishiftApplication.class.getClassLoader());
				clases.put(clase.getName(), clase);
				Set<String> referencias = leerReferenciasCompiladas(lector);
				añadirDeclaraciones(clase, referencias);
				dependencias.put(clase, referencias);
			}
		}
	}

	@Test
	void controladoresDeleganSinAccederAJdbcNiRepositorios() {
		verificar(capa(Controller.class), referencia -> usaJdbc(referencia)
				|| pertenece(referencia, Repository.class), "El controlador debe delegar la persistencia en servicios");
	}

	@Test
	void serviciosYColaboradoresDeNegocioNoDependenDeJdbcNiTransporteHttp() {
		Set<Class<?>> negocio = capa(Service.class);
		var pendientes = new ArrayDeque<>(negocio);
		// Incluye reglas @Component utilizadas por servicios y sus colaboradores de negocio.
		// Los DAO son el límite de persistencia: no se recorren sus propias dependencias.
		while (!pendientes.isEmpty()) {
			Class<?> actual = pendientes.removeFirst();
			for (String referencia : dependencias.get(actual)) {
				Class<?> colaborador = clases.get(referencia);
				if (colaborador != null && anotada(colaborador, Component.class)
						&& !anotada(colaborador, Repository.class) && !anotada(colaborador, Controller.class)
						&& negocio.add(colaborador)) {
					pendientes.addLast(colaborador);
				}
			}
		}
		// La coordinación de transacciones con Spring TransactionTemplate permanece permitida.
		verificar(negocio, referencia -> usaJdbc(referencia) || usaHttp(referencia)
				|| pertenece(referencia, Controller.class), "La lógica de negocio debe trabajar sin JDBC ni HTTP");
	}

	@Test
	void repositoriosNoDependenDeServiciosNiControladores() {
		verificar(capa(Repository.class), referencia -> pertenece(referencia, Service.class)
				|| pertenece(referencia, Controller.class), "El DAO no debe depender de las capas superiores");
	}

	private static Set<Class<?>> capa(Class<? extends Annotation> anotacion) {
		Set<Class<?>> resultado = new HashSet<>();
		clases.values().stream().filter(clase -> anotada(clase, anotacion)).forEach(resultado::add);
		assertFalse(resultado.isEmpty(), "No se encontró la capa identificada por @" + anotacion.getSimpleName());
		return resultado;
	}

	private static boolean pertenece(String referencia, Class<? extends Annotation> anotacion) {
		Class<?> clase = clases.get(referencia);
		return clase != null && anotada(clase, anotacion);
	}

	private static boolean anotada(Class<?> clase, Class<? extends Annotation> anotacion) {
		return AnnotatedElementUtils.hasAnnotation(clase, anotacion);
	}

	private static boolean usaJdbc(String referencia) {
		return List.of("java.sql.", "javax.sql.", "org.springframework.jdbc.").stream()
				.anyMatch(referencia::startsWith);
	}

	private static boolean usaHttp(String referencia) {
		return List.of("jakarta.servlet.", "org.springframework.web.", "org.springframework.http.").stream()
				.anyMatch(referencia::startsWith);
	}

	private static void verificar(Set<Class<?>> capa, Predicate<String> prohibida, String regla) {
		var infracciones = capa.stream().flatMap(clase -> dependencias.get(clase).stream()
				.filter(prohibida).map(referencia -> clase.getSimpleName() + " -> " + referencia))
				.sorted().toList();
		assertTrue(infracciones.isEmpty(), () -> regla + ":\n" + String.join("\n", infracciones));
	}

	private static Set<String> leerReferenciasCompiladas(ClassReader lector) {
		Set<String> referencias = new HashSet<>();
		char[] caracteres = new char[lector.getMaxStringLength()];
		// Se interpretan entradas tipadas del constant pool de la JVM; no se buscan cadenas del código fuente.
		// También alcanza llamadas internas de métodos, constructores, casts y referencias de lambdas.
		for (int indice = 1; indice < lector.getItemCount(); indice++) {
			int posicion = lector.getItem(indice);
			if (posicion == 0) { continue; } // Long y double reservan dos posiciones.
			int tipoEntrada = lector.readByte(posicion - 1);
			if (tipoEntrada == REFERENCIA_CLASE) {
				añadirTipo((Type) lector.readConst(indice, caracteres), referencias);
			} else if (tipoEntrada == NOMBRE_Y_TIPO || tipoEntrada == TIPO_METODO) {
				int posicionDescriptor = tipoEntrada == NOMBRE_Y_TIPO ? posicion + 2 : posicion;
				añadirTipo(Type.getType(lector.readUTF8(posicionDescriptor, caracteres)), referencias);
			}
		}
		return referencias;
	}

	private static void añadirTipo(Type tipo, Set<String> referencias) {
		if (tipo.getSort() == Type.OBJECT) {
			referencias.add(tipo.getClassName());
		} else if (tipo.getSort() == Type.ARRAY) {
			añadirTipo(tipo.getElementType(), referencias);
		} else if (tipo.getSort() == Type.METHOD) {
			añadirTipo(tipo.getReturnType(), referencias);
			Arrays.stream(tipo.getArgumentTypes()).forEach(argumento -> añadirTipo(argumento, referencias));
		}
	}

	private static void añadirDeclaraciones(Class<?> clase, Set<String> referencias) {
		añadirAnotaciones(clase, referencias);
		añadirGenerico(clase.getGenericSuperclass(), referencias, new HashSet<>());
		Arrays.stream(clase.getGenericInterfaces()).forEach(tipo -> añadirGenerico(tipo, referencias, new HashSet<>()));
		Arrays.stream(clase.getTypeParameters()).forEach(tipo -> añadirGenerico(tipo, referencias, new HashSet<>()));
		for (var campo : clase.getDeclaredFields()) {
			añadirGenerico(campo.getGenericType(), referencias, new HashSet<>());
			añadirAnotaciones(campo, referencias);
		}
		for (var metodo : clase.getDeclaredMethods()) {
			añadirGenerico(metodo.getGenericReturnType(), referencias, new HashSet<>());
			añadirEjecutable(metodo, referencias);
		}
		for (var constructor : clase.getDeclaredConstructors()) {
			añadirEjecutable(constructor, referencias);
		}
	}

	private static void añadirEjecutable(java.lang.reflect.Executable ejecutable, Set<String> referencias) {
		añadirAnotaciones(ejecutable, referencias);
		Arrays.stream(ejecutable.getGenericParameterTypes())
				.forEach(tipo -> añadirGenerico(tipo, referencias, new HashSet<>()));
		Arrays.stream(ejecutable.getGenericExceptionTypes())
				.forEach(tipo -> añadirGenerico(tipo, referencias, new HashSet<>()));
		Arrays.stream(ejecutable.getTypeParameters())
				.forEach(tipo -> añadirGenerico(tipo, referencias, new HashSet<>()));
		Arrays.stream(ejecutable.getParameters()).forEach(parametro -> añadirAnotaciones(parametro, referencias));
	}

	private static void añadirAnotaciones(AnnotatedElement elemento, Set<String> referencias) {
		Arrays.stream(elemento.getDeclaredAnnotations()).forEach(anotacion -> referencias.add(anotacion.annotationType().getName()));
	}

	private static void añadirGenerico(java.lang.reflect.Type tipo, Set<String> referencias,
			Set<java.lang.reflect.Type> visitados) {
		if (tipo == null || !visitados.add(tipo)) { return; }
		if (tipo instanceof Class<?> clase) {
			if (clase.isArray()) { añadirGenerico(clase.getComponentType(), referencias, visitados); }
			else if (!clase.isPrimitive()) { referencias.add(clase.getName()); }
		} else if (tipo instanceof ParameterizedType parametrizado) {
			añadirGenerico(parametrizado.getRawType(), referencias, visitados);
			añadirGenerico(parametrizado.getOwnerType(), referencias, visitados);
			Arrays.stream(parametrizado.getActualTypeArguments()).forEach(argumento -> añadirGenerico(argumento, referencias, visitados));
		} else if (tipo instanceof GenericArrayType arreglo) {
			añadirGenerico(arreglo.getGenericComponentType(), referencias, visitados);
		} else if (tipo instanceof TypeVariable<?> variable) {
			Arrays.stream(variable.getBounds()).forEach(limite -> añadirGenerico(limite, referencias, visitados));
		} else if (tipo instanceof WildcardType comodin) {
			Arrays.stream(comodin.getUpperBounds()).forEach(limite -> añadirGenerico(limite, referencias, visitados));
			Arrays.stream(comodin.getLowerBounds()).forEach(limite -> añadirGenerico(limite, referencias, visitados));
		}
	}
}
