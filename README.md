# MediShift

Aplicación web para organizar los turnos médicos y las citas de una institución de salud. Incluye espacios para administradores, doctores y pacientes.

## Funciones

- Gestión de profesionales, especialidades, áreas y consultorios.
- Calendario mensual con aprobación, reapertura e historial de versiones.
- Autoprogramación médica habilitada por el administrador y control de cruces y carga horaria.
- Registro de pacientes con consulta de DNI en ApisPerú y ficha administrativa.
- Calendario mensual de citas con espacios libres y registro de pacientes sin salir de la agenda.
- Reprogramación, estados y prevención de reservas simultáneas dentro de turnos aprobados.
- Exportación de horarios y citas a Excel y PDF, con filtros de área, consultorio y médico para las citas.
- Portal del paciente con ingreso por DNI y calendario de citas de los hospitales donde tiene una ficha activa.
- Registro de faltas del personal y feriados.
- Perfil de cuenta y configuración institucional con permisos por rol.

Para activar su acceso, el paciente usa el código que le entrega el hospital después de verificar su DNI y crea su contraseña.

## Tecnologías

Java 21, Spring Boot, PostgreSQL, React y Vite.

## Ejecución local

Requisitos: JDK 21, PostgreSQL, Node.js y pnpm.

1. Preparar PostgreSQL siguiendo la [guía de migraciones](db/migraciones/README.md). No ejecutar la línea base sobre una base existente.
2. Copiar [server/.env.example](server/.env.example) como `server/.env` y completar la conexión. El archivo está excluido de Git.
3. Iniciar el backend desde `server`:

```powershell
.\mvnw.cmd spring-boot:run
```

4. En otra terminal, iniciar el cliente desde `client`:

```powershell
pnpm install
pnpm dev
```

Abrir [localhost:5173](http://localhost:5173). Vite dirige las solicitudes `/api` al backend en el puerto 8080.

## Pruebas

Desde `server`, ejecutar `.\mvnw.cmd verify`. Desde `client`, ejecutar `pnpm test` y `pnpm build`.

Consulta la [documentación del backend](server/README.md), el [flujo de pacientes y citas](server/README.md#pacientes-y-citas) y los [datos de demostración](db/README-demostracion.md).

Licencia [MIT](LICENSE).
