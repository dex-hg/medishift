# Backend de MediShift

Spring Boot 4.1.1, Java 21 y PostgreSQL. El backend comprueba la conexión durante el arranque y expone el registro de una institución con su primera cuenta, además de iniciar, consultar y cerrar su sesión. El servidor HTTP escucha en el puerto 8080 de forma predeterminada.

## Configuración local

Ejecuta los comandos desde `server`. Configura ese mismo directorio de trabajo si utilizas un IDE. Completa la contraseña de PostgreSQL en tu `.env`. Si todavía no existe, crea una copia de la plantilla:

```powershell
Copy-Item -LiteralPath .\.env.example -Destination .\.env
```

Conserva los valores de un `.env` existente. El archivo está excluido de Git y no se debe compartir.

| Variable                    | Configuración local                                           |
| --------------------------- | -------------------------------------------------------------- |
| `MEDISHIFT_BD_URL`        | `jdbc:postgresql://localhost:5432/MediShift`                 |
| `MEDISHIFT_BD_USUARIO`    | `postgres`                                                   |
| `MEDISHIFT_BD_CLAVE`      | Contraseña de PostgreSQL, obligatoria                         |
| `MEDISHIFT_COOKIE_SEGURA` | `false` en localhost HTTP; `true` al servir mediante HTTPS |

La URL debe coincidir con el nombre exacto de la base, incluidas las mayúsculas. El esquema se toma de la configuración del usuario de PostgreSQL; si necesitas especificarlo, añade `?currentSchema=NOMBRE_ESQUEMA` a la URL.

Spring Boot carga `.env` con `spring.config.import` en formato de propiedades y codificación UTF-8. Escribe `CLAVE=valor` sin comillas ni `export`. Una barra invertida literal se escribe como `\\`; las comillas pasarían a formar parte del valor. Las variables del entorno de ejecución tienen prioridad sobre el archivo.

## Ejecutar

Se requiere un JDK compatible con Java 21 y PostgreSQL activo. El Maven Wrapper descarga su distribución y dependencias en el primer uso. El frontend y el backend son dos procesos separados: `pnpm dev` solo inicia Vite.

Abre dos terminales desde la raíz `medishift`. En la primera, inicia el backend:

```powershell
Set-Location .\server
.\mvnw.cmd spring-boot:run
```

Espera a que aparezcan la conexión PostgreSQL verificada y el mensaje de Tomcat iniciado en el puerto 8080. Mantén esta terminal abierta. Si ya estás dentro de `server`, ejecuta solo el comando del wrapper. En Linux o macOS utiliza `sh ./mvnw spring-boot:run`.

En la segunda terminal, inicia el frontend:

```powershell
Set-Location .\client
pnpm dev
```

Si ya estás dentro de `client`, ejecuta solo `pnpm dev`. Abre `http://127.0.0.1:5173/registro` o `/iniciar-sesion`. Vite redirige `/api/registro` y `/api/sesion` al backend en `localhost:8080`. Mantén ambas terminales abiertas mientras utilizas la aplicación.

La conexión correcta muestra `Conexión con PostgreSQL verificada correctamente.`. La falta de contraseña, una clave vacía o una conexión inválida impiden arrancar. La comprobación inicial es de solo lectura. `spring.sql.init.mode=never` mantiene desactivada la ejecución automática de scripts y no se incluyen cambios de esquema.

Si Vite muestra `ECONNREFUSED` al solicitar `/api/registro` o `/api/sesion`, comprueba la terminal del backend: ese mensaje indica que no pudo conectarse al servicio de `localhost:8080`. Inicia el backend o resuelve el error que impidió su arranque, y vuelve a intentar la solicitud. Si falla la conexión con PostgreSQL, revisa el servicio y la configuración local antes de reiniciar. No basta con tener Vite activo en el puerto 5173.

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

| Estado | Situación                            |
| ------ | ------------------------------------- |
| 400    | Campos inválidos o JSON incorrecto   |
| 409    | Código de institución ya existente  |
| 503    | No se puede acceder a PostgreSQL      |
| 500    | Fallo interno con respuesta genérica |

Las respuestas no incluyen contraseñas, hashes, SQL ni mensajes internos. Solo se incorpora `spring-security-crypto` para el hash, sin activar la seguridad web completa.

## Inicio y cierre de sesión

`POST /api/sesion` recibe tres campos de texto: `codigoInstitucion`, `correo` y `contrasena`. El código conserva sus mayúsculas y minúsculas, el correo se compara sin distinguirlas dentro de la institución y la contraseña se verifica exactamente, sin recortarla. Usa los mismos límites y reglas del registro para esos campos. Un ejemplo:

```json
{"codigoInstitucion":"Clinica-Lima","correo":"cuenta@institucion.pe","contrasena":"una-contraseña-de-ejemplo"}
```

Si la cuenta y la institución están activas y el hash PBKDF2 coincide, responde HTTP 200 con `idCuenta`, `idInstitucion`, `codigoInstitucion`, `nombreInstitucion` y `correo`, sin contraseña ni hash. Un código incorrecto, un correo ajeno a esa institución, una contraseña errónea o una cuenta inactiva producen el mismo error HTTP 401. La validación de campos y JSON produce 400; la falta de conexión con PostgreSQL, 503.

El inicio reemplaza cualquier sesión anterior y rota su identificador. La sesión HTTP contiene únicamente los UUID de cuenta e institución. `GET /api/sesion` devuelve los mismos cinco datos con HTTP 200 si la sesión aún existe y ambos estados siguen activos; ante una cuenta o institución revocada, invalida la sesión y devuelve 401. `DELETE /api/sesion` cierra la sesión y devuelve 204, incluso si ya estaba cerrada. Los tres métodos incluyen `Cache-Control: no-store` en sus respuestas.

La sesión caduca tras 30 minutos de inactividad. Viaja solo por cookie `HttpOnly` y `SameSite=Strict`; no se admite el identificador en la URL. `MEDISHIFT_COOKIE_SEGURA=false` permite probar en `localhost` por HTTP. Configura `MEDISHIFT_COOKIE_SEGURA=true` al servir mediante HTTPS para marcar la cookie como `Secure`. El cliente debe enviar la cookie en las solicitudes a `/api/sesion`; no debe guardar el identificador ni la contraseña en almacenamiento del navegador. Esta etapa no protege todavía futuros endpoints de gestión, que requerirán autorización al implementarse.

Las sesiones se conservan en la memoria del servidor y se pierden al reiniciarlo. Antes de publicar el servicio, debe incorporarse una política de limitación de intentos de autenticación. Los profesionales, consultorios y horarios del panel siguen siendo datos de demostración locales; el registro y la autenticación sí utilizan PostgreSQL.

## Pruebas

```powershell
.\mvnw.cmd verify
```

Las pruebas habituales no acceden a la base. Cubren validación, normalización, hash, contrato HTTP, errores y orden de las operaciones transaccionales. Las pruebas reales se activan expresamente con credenciales válidas y el esquema existente:

```powershell
.\mvnw.cmd "-Dmedishift.prueba-conexion=true" "-Dmedishift.prueba-registro=true" "-Dmedishift.prueba-sesion=true" verify
```

`RegistroPostgresqlTests` comprueba persistencia y hash, duplicados, correo por institución y reversión cuando falla la segunda inserción. `SesionPostgresqlTests` verifica la misma cuenta real, la separación entre instituciones y la revocación por estado. Las pruebas generan códigos y UUID aleatorios y eliminan únicamente sus propias instituciones y cuentas por UUID al finalizar. No ejecutan DDL. Sin las opciones indicadas, las pruebas reales quedan omitidas.

## Archivos de Git

Versiona los fuentes, pruebas, `pom.xml`, README, `.env.example` y los wrappers `mvnw`, `mvnw.cmd` y `.mvn/wrapper/maven-wrapper.properties`. Las credenciales y `target` están excluidos de Git.

Referencias oficiales: [configuración externa de Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html), [propiedades del servidor y la sesión](https://docs.spring.io/spring-boot/appendix/application-properties/), [cookies SameSite](https://docs.spring.io/spring-boot/reference/web/servlet.html), [almacenamiento de contraseñas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) y [API de sesiones Servlet](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletrequest).
