# Migraciones del núcleo mensual

V001 incorpora `planning_unit`, la modalidad de `schedule` y las claves de versión y aprobación por unidad y mes. El esquema `db/medishift_postgresql.sql` se conserva como línea base previa: no debe volver a ejecutarse sobre una base existente. Spring mantiene `spring.sql.init.mode=never`; estas migraciones no se aplican al iniciar la aplicación.

## Estado verificado

El 16 de octubre de 2026 se ensayaron V001 y U001 contra PostgreSQL local, dentro de esquemas aleatorios y transacciones revertidas. Se comprobaron la conservación de filas y referencias históricas, los meses de 28 a 31 días, el aislamiento institucional, las versiones y aprobaciones por unidad y la protección del contrato semanal. No se aplicó V001 a `public`.

Las filas anteriores conservan sus identificadores, fechas, estados, versiones y referencias. Reciben `weekly` y unidad nula; esa etiqueta preserva el comportamiento anterior y no certifica que cada periodo histórico tenga siete días. El ensayo incluye un periodo legado de catorce días. No se fusionan semanas ni se crean servicios ficticios.

Las unidades admiten los tipos internos `department` y `service`, sin jerarquía. Sus códigos son únicos por institución y conservan mayúsculas y minúsculas. El mes exige una unidad de la misma institución, primer y último día correctos y año entre 0001 y 9999. Las exclusiones de `shift` siguen siendo globales para el profesional y el consultorio.

## Ensayo reproducible

Desde `C:\Proyectos\medishift\server`, con `.env` local válido y la extensión `btree_gist` ya instalada:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
.\mvnw.cmd -o '-Dmedishift.prueba-migracion-mensual=true' '-Dtest=MigracionMensualPostgresqlTests,RepositorioProgramacionTests' test
```

El JDK mostrado corresponde al entorno validado; si cambia la instalación, ajustar su ruta. `-o` utiliza las dependencias disponibles en el repositorio Maven local. En una instalación nueva deben descargarse previamente.

`SoporteMigracionMensual` obtiene una conexión, inicia una transacción y crea un esquema `prueba_mensual_<UUID>`. Carga allí la línea base sin sus instrucciones de transacción ni instalación de extensiones y ejecuta V001. Cada prueba termina con `rollback`, incluso si falla. No utiliza `DROP SCHEMA CASCADE`, no instala extensiones y no confirma DDL ni fixtures. Una cuenta sin permiso para crear esquemas no puede ejecutar este ensayo; el fallo no se convierte en una prueba aprobada.

## Aplicación operativa posterior

No hay un ejecutor automático ni una tabla de control de migraciones en esta entrega. Los nombres versionados no proporcionan por sí solos seguimiento de aplicaciones. Antes de habilitar una base mensual, implementar ese control o conservar un registro operativo explícito con base, versión, SHA-256, fecha, responsable y resultado. No registrar una aplicación antes de que su transacción se confirme.

La futura aplicación requiere estos pasos:

1. Revisar el inventario de `schedule`: institución, identificadores, fechas, longitud del periodo, versiones, estados, autores y referencias desde `shift`, cobertura y generación. Identificar periodos atípicos antes de aceptar su clasificación heredada. No modificar ni borrar anomalías automáticamente.
2. Obtener un respaldo completo con una cuenta autorizada y comprobar su restauración en una base separada. No guardar credenciales en comandos, archivos versionados ni evidencias compartidas. Registrar también conteos y huellas de filas y definiciones del esquema.
3. Ensayar la migración sobre una copia representativa del respaldo, comprobar los resultados y conservar el informe. El ensayo sintético existente no sustituye esta comprobación del respaldo real.
4. Desplegar primero la protección semanal compatible. Coordinar una ventana sin escrituras y fijar límites de espera para los bloqueos. Las operaciones DDL necesitan bloqueos; su duración depende del volumen real.
5. Ejecutar V001 una sola vez con `ON_ERROR_STOP` y una transacción única. Verificar claves y datos antes de habilitar funciones nuevas; registrar la aplicación confirmada y el SHA-256 del archivo exacto.

Ejemplo de ejecución futura desde la raíz del checkout, con conexión previamente configurada mediante los mecanismos locales de PostgreSQL:

```powershell
psql -X --set=ON_ERROR_STOP=1 --single-transaction --file=db/migraciones/V001__nucleo_mensual.sql
```

Es un procedimiento documentado; no se ejecutó sobre la base operativa. No ejecutar el archivo con confirmación automática por sentencia: una interrupción podría dejar una migración parcial. Una segunda ejecución encuentra objetos existentes y falla; debe investigarse el registro de aplicaciones, sin saltar sentencias ni usar `IF NOT EXISTS` para ocultar diferencias.

Para inventariar la distribución de periodos sin modificar datos:

```sql
SELECT period_end_schedule - period_start_schedule + 1 AS dias,
       status_schedule, count(*) AS cantidad
FROM schedule
GROUP BY dias, status_schedule
ORDER BY dias, status_schedule;
```

La consulta agrupa los datos; el inventario individual y las referencias se revisan en el entorno autorizado y no se publican como evidencia académica.

## Recuperación

Un error durante la transacción de V001 exige `rollback`: PostgreSQL conserva el esquema anterior. Antes de confirmar, no se necesita U001.

Después de confirmar V001, U001 permite retirar la ampliación solamente si no hay unidades ni planes mensuales. Toma un bloqueo exclusivo y rechaza cualquier unidad o plan mensual, incluidos planes cancelados y unidades sin planes. Los horarios y turnos semanales nuevos se conservan al restaurar las restricciones anteriores. Se ensayó la igualdad de columnas, restricciones, índices y filas antes y después.

```powershell
psql -X --set=ON_ERROR_STOP=1 --single-transaction --file=db/migraciones/U001__retirar_nucleo_mensual.sql
```

Si ya hay datos nuevos, conservarlos y preparar una migración correctiva o una recuperación mediante el respaldo comprobado. No eliminar unidades ni planes para forzar U001. El SQL de recuperación no concede autorización para descartar información operativa.

## Límites de este incremento

El esquema admite cabeceras mensuales sintéticas y conserva sus claves; no implementa un servicio de creación ni autorización mensual. No expone rutas, configura cuotas, asigna actividades ni aprueba meses desde la aplicación. Las restricciones de base no sustituyen las reglas de negocio, los permisos ni la auditoría.

Antes de permitir asignaciones mensuales deben implementarse las relaciones de actividad, elegibilidad y recursos, la disponibilidad nocturna y las políticas aplicables. En particular, el modelo y los lectores actuales de `shift` aún exigen consultorio y especialidad. El contrato futuro está en [contrato-planes-mensuales.md](../../server/contrato-planes-mensuales.md).
