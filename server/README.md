# Backend de MediShift

Spring Boot 4.1.1, Java 21 y PostgreSQL. El backend comprueba la conexión durante el arranque y expone el registro de una institución con su primera cuenta. El servidor HTTP escucha en el puerto 8080 de forma predeterminada.

## Configuración local

Ejecuta los comandos desde `server`. Configura ese mismo directorio de trabajo si utilizas un IDE. Completa la contraseña de PostgreSQL en tu `.env`. Si todavía no existe, crea una copia de la plantilla:

```powershell
Copy-Item -LiteralPath .\.env.example -Destination .\.env
```

Conserva los valores de un `.env` existente. El archivo está excluido de Git y no se debe compartir.

| Variable | Configuración local |
| --- | --- |
| `MEDISHIFT_BD_URL` | `jdbc:postgresql://localhost:5432/MediShift` |
| `MEDISHIFT_BD_USUARIO` | `postgres` |
| `MEDISHIFT_BD_CLAVE` | Contraseña de PostgreSQL, obligatoria |

La URL debe coincidir con el nombre exacto de la base, incluidas las mayúsculas. El esquema se toma de la configuración del usuario de PostgreSQL; si necesitas especificarlo, añade `?currentSchema=NOMBRE_ESQUEMA` a la URL.

Spring Boot carga `.env` con `spring.config.import` en formato de propiedades y codificación UTF-8. Escribe `CLAVE=valor` sin comillas ni `export`. Una barra invertida literal se escribe como `\\`; las comillas pasarían a formar parte del valor. Las variables del entorno de ejecución tienen prioridad sobre el archivo.

## Ejecutar

Se requiere un JDK compatible con Java 21 y PostgreSQL activo. El Maven Wrapper descarga su distribución y dependencias en el primer uso.

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux o macOS utiliza `sh ./mvnw spring-boot:run`.

Para probar el formulario, inicia el cliente en otra terminal desde `client` con `pnpm run dev` y abre `http://127.0.0.1:5173/registro`. El cliente envía `/api/registro` al backend de `localhost:8080` mediante el proxy de Vite ya configurado. Mantén ambos procesos activos durante el registro.

La conexión correcta muestra `Conexión con PostgreSQL verificada correctamente.`. La falta de contraseña, una clave vacía o una conexión inválida impiden arrancar. La comprobación inicial es de solo lectura. `spring.sql.init.mode=never` mantiene desactivada la ejecución automática de scripts y no se incluyen cambios de esquema.

## Registro

`POST /api/registro` acepta `Content-Type: application/json` con los datos siguientes. La confirmación de contraseña se comprueba en el cliente y no se envía.

```json
{
  "codigoInstitucion": "Clinica-Lima",
  "nombreInstitucion": "Clínica Lima",
  "zonaHoraria": "America/Lima",
  "correo": "cuenta@institucion.pe",
  "contrasena": "una-contraseña-de-ejemplo"
}
```

Los cinco campos deben ser texto. El código y el nombre se recortan con las mismas reglas de espacios Unicode de JavaScript. El código conserva mayúsculas y minúsculas, según la restricción única de PostgreSQL. El correo se recorta y se convierte a minúsculas; su unicidad se aplica dentro de cada institución. Se admite el mismo patrón de correo del cliente, incluidos dominios locales como `persona@localhost`.

Los límites son 32 caracteres para el código, 140 para el nombre, 64 para la zona horaria y 254 para el correo. Se cuentan puntos de código Unicode, como PostgreSQL, y se rechazan NUL y secuencias UTF-16 mal formadas. La zona debe coincidir exactamente con una de las seis opciones del cliente: `America/Lima`, `America/Bogota`, `America/Santiago`, `America/La_Paz`, `America/Mexico_City` y `America/Argentina/Buenos_Aires`.

La contraseña no puede ser vacía ni estar compuesta solo por espacios. No se recorta ni se modifica. Tiene un límite operativo de 1024 puntos de código para acotar el procesamiento de cada solicitud. Se guarda un hash PBKDF2 con HMAC SHA-256, salt aleatorio de 16 bytes y 600 000 iteraciones. El prefijo `{pbkdf2@medishift-v1}` identifica esos parámetros para su futura verificación. El cálculo se realiza antes de abrir la transacción.

La operación inserta una institución y una cuenta activas, con UUID generados en la aplicación. PostgreSQL asigna `created_at_user_account` mediante su valor predeterminado. Las dos inserciones usan parámetros preparados y una sola transacción: si falla la segunda, la primera se revierte. No se crean roles ni se asignan permisos.

El éxito responde con HTTP 201:

```json
{
  "idInstitucion": "00000000-0000-4000-8000-000000000001",
  "idCuenta": "00000000-0000-4000-8000-000000000002",
  "codigoInstitucion": "Clinica-Lima",
  "nombreInstitucion": "Clínica Lima",
  "correo": "cuenta@institucion.pe"
}
```

Los errores tienen la forma `{"mensaje":"Descripción","errores":{"campo":"Descripción"}}`. Los errores sin un campo específico usan `errores: {}`.

| Estado | Situación |
| --- | --- |
| 400 | Campos inválidos o JSON incorrecto |
| 409 | Código de institución ya existente |
| 503 | No se puede acceder a PostgreSQL |
| 500 | Fallo interno con respuesta genérica |

Las respuestas no incluyen contraseñas, hashes, SQL ni mensajes internos. Esta etapa proporciona el registro; no implementa inicio de sesión, sesiones o autorización. Solo se incorpora `spring-security-crypto` para el hash, sin activar la seguridad web completa.

## Pruebas

```powershell
.\mvnw.cmd verify
```

Las pruebas habituales no acceden a la base. Cubren validación, normalización, hash, contrato HTTP, errores y orden de las operaciones transaccionales. Las pruebas reales se activan expresamente con credenciales válidas y el esquema existente:

```powershell
.\mvnw.cmd "-Dmedishift.prueba-conexion=true" "-Dmedishift.prueba-registro=true" verify
```

`RegistroPostgresqlTests` comprueba persistencia y hash, duplicados, correo por institución y reversión cuando falla la segunda inserción. Genera códigos y UUID aleatorios y elimina únicamente sus propias instituciones y cuentas por UUID al finalizar cada prueba. No ejecuta DDL. Sin las opciones indicadas, las pruebas reales quedan omitidas.

## Archivos de Git

Versiona los fuentes, pruebas, `pom.xml`, README, `.env.example` y los wrappers `mvnw`, `mvnw.cmd` y `.mvn/wrapper/maven-wrapper.properties`. Las credenciales y `target` están excluidos de Git.

Referencias oficiales: [configuración externa de Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html), [bases SQL](https://docs.spring.io/spring-boot/reference/data/sql.html), [almacenamiento de contraseñas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) y [constructor de Pbkdf2PasswordEncoder](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html).
