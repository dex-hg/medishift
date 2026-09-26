# Backend de MediShift

Backend creado con Spring Initializr, Spring Boot 4.1.1 y objetivo Java 21. Esta etapa configura la conexión con la base PostgreSQL existente y la comprueba durante el arranque.

## Configuración local

Ejecuta los comandos desde `server`. Si ejecutas la aplicación desde un IDE, configura también `server` como directorio de trabajo.

Completa `MEDISHIFT_BD_CLAVE` en tu `.env` antes de arrancar. Al clonar el repositorio, crea el archivo a partir de la plantilla:

```powershell
Copy-Item -LiteralPath .\.env.example -Destination .\.env
```

Si ya tienes un `.env`, edítalo directamente para conservar tus valores.

| Variable | Configuración local |
| --- | --- |
| `MEDISHIFT_BD_URL` | `jdbc:postgresql://localhost:5432/MediShift` |
| `MEDISHIFT_BD_USUARIO` | `postgres` |
| `MEDISHIFT_BD_CLAVE` | Contraseña de PostgreSQL, obligatoria |

La URL debe usar el nombre exacto registrado en PostgreSQL, incluidas las mayúsculas. Si la base se registró como `medishift`, corrige la URL en `.env`. El esquema se toma de la configuración del usuario de PostgreSQL; si necesitas especificarlo, añade `?currentSchema=NOMBRE_ESQUEMA` a la URL.

Spring Boot carga explícitamente `.env` mediante `spring.config.import`, usando el formato de propiedades y codificación UTF-8. Escribe `CLAVE=valor` sin comillas ni el prefijo `export`. Una barra invertida literal se escribe como `\\`; las comillas, si las añades, formarían parte del valor. Las variables del entorno de ejecución también son válidas y tienen prioridad sobre el archivo.

## Ejecución

Se requiere un JDK compatible con Java 21 y PostgreSQL activo. El Maven Wrapper descarga su distribución y las dependencias en el primer uso.

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux o macOS utiliza `sh ./mvnw spring-boot:run`.

La comprobación correcta muestra `Conexión con PostgreSQL verificada correctamente.`. Si falta la contraseña, está vacía o la conexión falla, la aplicación rechaza el arranque. Revisa el servicio en `localhost:5432`, el nombre de la base, las credenciales y los permisos de conexión del usuario.

Esta etapa todavía no levanta un servidor HTTP, de modo que el proceso puede finalizar después de verificar la conexión. La comprobación no modifica tablas ni datos y la inicialización automática de scripts SQL está desactivada.

## Pruebas

```powershell
.\mvnw.cmd verify
```

Las pruebas unitarias verifican credenciales ausentes o vacías, conexiones inválidas, errores SQL y el cierre de recursos. La prueba de integración con PostgreSQL se activa expresamente y requiere un `.env` válido o variables de entorno equivalentes:

```powershell
.\mvnw.cmd "-Dmedishift.prueba-conexion=true" test
```

Sin esa opción, `MedishiftApplicationTests` queda omitida. Las pruebas con conexiones simuladas no demuestran conectividad con la base real.

## Archivos de Git

`.env` es local y está excluido de Git. `.env.example` se versiona con la contraseña vacía. Las reglas de Initializr se conservan dentro de `server/.gitignore` para excluir `target` y archivos generados por los IDE, y dentro de `server/.gitattributes` para mantener los finales de línea de los wrappers. El repositorio y el `.gitignore` de la raíz se conservan.

Se deben versionar `mvnw`, `mvnw.cmd` y `.mvn/wrapper/maven-wrapper.properties`; este wrapper usa distribución `only-script` y no necesita un JAR del wrapper. `target` y las credenciales no forman parte de los commits.

Referencias oficiales: [configuración externa de Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html), [bases SQL](https://docs.spring.io/spring-boot/reference/data/sql.html) e [inicialización de bases](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
