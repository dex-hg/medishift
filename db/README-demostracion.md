# Datos demostrativos para otra computadora

Ejecuta `db/datos_demostracion.sql` en la base de MediShift desde el editor de consultas de pgAdmin o con `psql`. Utiliza PostgreSQL y el mismo esquema de esta versión de la aplicación. No necesita extensiones adicionales a las del esquema original.

En una base nueva, instala primero `db/medishift_postgresql.sql`. Después configura `server/.env` y utiliza las herramientas de `db/migraciones/README.md` para instalar V001, V002 y V003 en orden, conservando sus respaldos, ensayos y registro de checksum. En una base ya utilizada, sigue ese mismo procedimiento de migraciones; el esquema inicial se utiliza únicamente para una base nueva. El script demostrativo comprueba los campos requeridos antes de insertar registros.

El archivo crea dos instituciones ficticias e independientes. Estas son sus credenciales públicas de demostración:

| Código de institución | Correo | Contraseña |
| --- | --- | --- |
| DEMO-FERIADOS-PE | admin.feriados.pe@example.test | FeriadosDemo2026! |
| DEMO-FERIADOS-CO | admin.feriados.co@example.test | FeriadosDemo2026! |

Cada institución tiene 9 especialistas, 3 especialidades, 3 áreas, 6 consultorios, 3 planes mensuales, 12 turnos en borrador y 2 faltas. Incluye un especialista sin turnos y dos turnos de Ana Torres en el mismo feriado, en distintos consultorios; uno de esos turnos tiene una falta. Los registros y sus auditorías están identificados como demostrativos.

La carga utiliza diciembre del año de ejecución; si se ejecuta durante diciembre, utiliza enero del siguiente año. Los mensajes de resultado indican el mes exacto. En octubre de 2026 se genera `2026-12`. Selecciona ese mes en **Horarios** después de iniciar sesión. Las instituciones emplean `America/Lima` y `America/Bogota`: comparten un desfase horario, pero consultan calendarios nacionales distintos.

Los feriados se obtienen desde la aplicación y se conservan en su copia local. El SQL carga los registros del sistema; no introduce un calendario nacional estático ni necesita claves de una API. La primera consulta del año en la nueva computadora requiere conexión a Internet para obtener los feriados. El botón **Actualizar feriados** permite reintentar la consulta.

Para comprobar las reglas, revisa que Ana Torres reciba un único abono por cada feriado del mes, aunque tenga dos turnos en esa fecha y una falta. «Persona Sin turnos» debe tener cero horas por feriados. Luis Ramos tiene una falta en su turno ordinario. En el reporte individual, los abonos se suman al profesional; en el reporte por consultorio se muestran aparte como institucionales.

La carga completa está dentro de una transacción. Repetirla conserva las instituciones ya creadas, sus datos y las contraseñas actuales, sin duplicar registros. Si el código reservado pertenece a otro identificador, la ejecución se cancela y se revierte. No restablece las contraseñas de una demostración que ya fue modificada.
