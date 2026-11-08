# Pacientes y agenda de citas

El módulo reutiliza los profesionales, cuentas de doctor, sesiones y turnos semanales y mensuales existentes. No crea un rol de paciente ni almacena diagnósticos, recetas, tratamientos o historia clínica.

## Reglas acordadas

- El administrador administra todas las fichas y citas de su institución. Cada doctor registra citas en su propia agenda, aunque la autoprogramación de turnos esté desactivada.
- Cada paciente tiene código interno, nombres, apellidos, DNI obligatorio de ocho dígitos, fecha de nacimiento, contacto opcional y estado. El DNI es único dentro de la institución.
- Un doctor busca pacientes institucionales por DNI y recibe solo su identidad administrativa mínima. La ficha completa y su edición requieren que el doctor haya registrado al paciente o tenga una cita vinculada. El historial del doctor muestra únicamente sus citas.
- Las citas se registran a futuro, exclusivamente dentro de turnos aprobados con médico y consultorio activos. El usuario elige inicio y fin; no hay una duración fija.
- Los intervalos son semiabiertos: una cita puede comenzar cuando termina la anterior. Se impiden cruces tanto del médico como del paciente, incluso entre agendas de distintos doctores.
- Una cita programada se puede reprogramar o cancelar. Puede marcarse atendida desde su inicio y no asistió desde su final. Los estados finales no se corrigen ni se eliminan en este alcance.
- Desactivar un paciente conserva su ficha y citas e impide nuevas reservas. Las citas ya programadas pueden cancelarse o finalizarse.
- Las modificaciones usan `If-Match`. Una revisión antigua devuelve 409 y una cabecera ausente devuelve 428.

## Aprobación mensual y cambios de horario

El administrador aprueba el mes completo de un área, incluidos todos sus consultorios. La operación vuelve a comprobar recursos, cruces, ausencias, bloqueos, carga horaria y cobertura existente. La revisión del plan incluye sus turnos, de modo que una edición concurrente exige volver a revisarlo.

Solo el administrador reabre un mes aprobado. Se guarda una copia de la versión aprobada en `historial_plan_mensual`, se incrementa la versión y se mantienen los identificadores del plan y de los turnos. Las citas permanecen vinculadas. Mientras el mes esté abierto no admite nuevas reservas ni reprogramaciones hacia sus turnos; las citas existentes sí conservan sus opciones de cancelación y cierre.

Antes de cambiar médico, consultorio o límites de un turno se comprueban sus citas no canceladas. Si alguna queda afectada, se devuelve 409 con sus identificadores, fechas y estados. Deben reprogramarse en otro turno aprobado o cancelarse antes de continuar. Se permite ampliar un turno cuando conserva las citas dentro de su intervalo.

Un turno con citas canceladas se retira mediante cambio de estado, sin borrarlo. Las citas atendidas o con inasistencia conservan su horario histórico. La cancelación o sustitución de una semana también queda bloqueada cuando conserva citas no canceladas.

## Instalación

Requiere V001 a V005. Desde la raíz:

```powershell
python -B scripts/administrar_pacientes_citas.py estado
python -B scripts/administrar_pacientes_citas.py ensayar
python -B scripts/administrar_pacientes_citas.py aplicar
```

`aplicar` vuelve a respaldar y restaurar una copia temporal, ensaya V006 y compara las huellas de las filas anteriores. Solo después aplica y registra su SHA-256 en una transacción. Los respaldos y resultados quedan en `server/target`, excluido de Git. Spring no ejecuta migraciones al arrancar. Reinicia el backend para cargar el código nuevo.

V006 crea `paciente`, `paciente_profesional`, `cita` e `historial_plan_mensual`. Todas las relaciones incluyen la institución. Las restricciones de exclusión de PostgreSQL impiden reservas simultáneas del médico y del paciente. Los servicios comparten el bloqueo institucional de programación; los triggers protegen además los vínculos y las citas al cambiar turnos o cancelar planes. La auditoría del paciente guarda versión y estado, sin duplicar DNI ni datos de contacto.

## API

Todas las rutas requieren sesión vigente de administrador o doctor. La institución y el actor se obtienen de la sesión; no se aceptan en el formulario. Las respuestas usan `Cache-Control: no-store` y las escrituras comprueban el origen.

| Método y ruta | Operación |
| --- | --- |
| `GET /api/pacientes?buscar=&pagina=0` | Listado institucional del administrador o pacientes vinculados al doctor; 30 por página |
| `GET /api/pacientes/buscar-dni?dni=12345678` | Búsqueda exacta de identidad mínima |
| `POST /api/pacientes` | Registro de ficha |
| `GET /api/pacientes/{id}` | Ficha autorizada y ETag |
| `PUT /api/pacientes/{id}` | Actualización con If-Match, incluida desactivación |
| `GET /api/pacientes/{id}/citas?pagina=0` | Historial autorizado, 30 por página |
| `GET /api/citas?fecha=AAAA-MM-DD` | Agenda del día y turnos aprobados autorizados |
| `GET /api/citas/{id}` | Cita autorizada y ETag |
| `POST /api/citas` | Reserva futura |
| `PUT /api/citas/{id}` | Reprogramación con If-Match y conservación del paciente |
| `PUT /api/citas/{id}/estado` | Estado final con If-Match |
| `POST /api/planes-mensuales/{id}/aprobar` | Aprobación administrativa con If-Match |
| `POST /api/planes-mensuales/{id}/reabrir` | Archivo de versión y reapertura administrativa con If-Match |
| `GET /api/planes-mensuales/{id}/historial` | Copias de las versiones aprobadas anteriores |

Paciente: `nombres`, `apellidos`, `dni`, `fechaNacimiento`, `correo`, `telefono`, `activo`. Cita: `idPaciente`, `idTurno`, `fecha`, `horaInicio`, `horaFin`. El cambio de estado recibe `estado`: `atendida`, `cancelada` o `no_asistio`. No existen operaciones DELETE para pacientes ni citas.

## Verificación

Desde `server`, `./mvnw.cmd verify` comprueba las reglas habituales. Para incluir las pruebas aisladas PostgreSQL de este incremento:

```powershell
.\mvnw.cmd "-Dmedishift.prueba-atencion=true" verify
```

Las pruebas crean y eliminan esquemas privados. Cubren duplicados, permisos de dos doctores, separación institucional, estados por tiempo, reprogramaciones, revisiones antiguas, intervalos contiguos, reservas simultáneas, reversión de auditoría, aprobación mensual y conservación de citas e historial. No insertan pacientes de prueba en las tablas reales de la institución.

Desde `client`, ejecutar `pnpm test` y `pnpm build`.
