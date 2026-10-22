# Cliente de MediShift

Interfaz React y Vite. Utiliza arquitectura por capas: páginas y componentes para presentación, hooks para coordinación, servicios para operaciones y acceso a API, y utilidades para validaciones locales.

## Ejecutar

Desde `client`, con las dependencias instaladas:

```powershell
node node_modules/vite/bin/vite.js
```

El backend debe estar activo por separado. Consulta su [configuración](../server/README.md). Vite redirige `/api` a Spring Boot; las solicitudes utilizan la cookie de sesión y no guardan contraseñas en el navegador.

Si faltan dependencias, instálalas con el gestor y el archivo de bloqueo del proyecto antes de ejecutar Vite.

## Verificar

```powershell
node --test src/services/*.test.js src/utils/*.test.js
node node_modules/vite/bin/vite.js build
```

Las pruebas actuales verifican servicios y validaciones con transporte inyectado. No incluyen una suite de componentes ni demuestran por sí solas el flujo de navegador contra PostgreSQL.

## Una sección para los horarios

El menú contiene una sola entrada Horarios. `/horarios` abre Meses y áreas para Administrador; `?vista=semanal` o los enlaces existentes con `fechaInicio` abren los turnos semanales. `/planes-mensuales` redirige a la sección unificada. La sesión aporta `esAdministrador` como booleano desde el servidor; no se infieren roles a partir del correo o del nombre. Las cuentas sin ese permiso conservan la vista semanal y la API mensual sigue denegando su acceso.

La vista mensual crea y consulta cabeceras por área o servicio. Las asignaciones y aprobaciones actuales siguen siendo semanales: reunir la navegación no une ambos modelos ni implementa la matriz mensual.

## Asignación directa y selección de recursos

El encargado asigna fecha y horas directamente, sin un formulario previo de disponibilidad. El módulo de disponibilidades y sus rutas se retiraron. El formulario de turnos solicita profesional, especialidad y consultorio compatible, en ese orden. Una especialidad única se selecciona automáticamente; con varias, se requiere elección explícita. Los mensajes indican si faltan recursos activos y permiten revisar sus catálogos. El consultorio continúa siendo una selección explícita y obligatoria.

La programación y aprobación operativas siguen siendo semanales. El núcleo mensual ya dispone de valores de calendario e intervalo; su integración con persistencia y API corresponde a M02, y la matriz mensual a M11. Cambiar las etiquetas de semana a mes depende de esa integración.

## Ejemplo de separación

El panel operativo sigue `Inicio.jsx -> useResumenInicio.js -> resumenInicio.js -> servicios API`. La vista presenta datos; el hook controla estados, cancelación y reintento; el servicio carga profesionales, consultorios y la semana actual en tres consultas paralelas. El backend calcula la semana en la zona institucional. Las reglas definitivas de acceso y programación siguen aplicándose en el servidor.
