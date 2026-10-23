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
node --test src/services/*.test.js src/utils/*.test.js src/reportes/*.test.js
node node_modules/vite/bin/vite.js build
```

Las pruebas actuales verifican servicios y validaciones con transporte inyectado. No incluyen una suite de componentes ni demuestran por sí solas el flujo de navegador contra PostgreSQL.

## Una sección para los horarios

El menú contiene una sola entrada Horarios. `/horarios` abre Meses y áreas para Administrador; `?vista=semanal` o los enlaces existentes con `fechaInicio` abren los turnos semanales. `/planes-mensuales` redirige a la sección unificada. La sesión aporta `esAdministrador` como booleano desde el servidor; no se infieren roles a partir del correo o del nombre. Las cuentas sin ese permiso conservan la vista semanal y la API mensual sigue denegando su acceso.

La vista mensual organiza consultorios por área y permite crear, editar, mover y eliminar sus turnos en borrador. Las asignaciones mensuales están persistidas; su aprobación sigue pendiente. El historial semanal conserva su flujo independiente.

## Reportes del calendario mensual

En `/horarios`, **Exportar horario** permite elegir el consultorio visible, todo el mes o un profesional. La ficha individual reúne todos sus consultorios y áreas del mes, con nombre, colegiatura como texto, días, franjas, horas y totales. PDF incluye una matriz mensual o un calendario y el detalle completo; Excel incluye la matriz individual y una tabla de turnos con fechas y horas numéricas, filtros y fórmulas para los totales de cada hoja. Son capturas del calendario consultado, no libros de planificación que se sincronicen entre hojas ni con el sistema al editarlos.

Los reportes identifican la institución, el mes, los estados, la zona horaria y el momento de exportación. No incluyen turnos cancelados o sustituidos ni convierten un borrador en una aprobación. Para incorporar cambios de otros usuarios, actualizar el calendario antes de abrir el reporte. Los datos proceden de `/api/planificador`, protegido por sesión, institución y permiso administrativo; no se crea una API pública para exportar.

En navegadores de escritorio con `showSaveFilePicker` y contexto seguro (HTTPS o localhost), **Guardar PDF/Excel como…** abre el selector de nombre y carpeta antes de generar el archivo. Cancelarlo no dispara una descarga alternativa. La casilla **Usar la descarga del navegador** ofrece una alternativa explícita. En otros navegadores se utiliza la descarga habitual y se indica cómo activar la pregunta de destino en su configuración. Ninguna página web puede imponer una carpeta mediante una ruta escrita manualmente.

Las librerías de PDF y Excel se cargan al exportar. Vite las preoptimiza para que su primera utilización en desarrollo no recargue el calendario. `core-js` no requiere ejecutar su script informativo de instalación.

## Asignación directa y selección de recursos

El encargado asigna fecha y horas directamente, sin un formulario previo de disponibilidad. El módulo de disponibilidades y sus rutas se retiraron. El formulario de turnos solicita profesional, especialidad y consultorio compatible, en ese orden. Una especialidad única se selecciona automáticamente; con varias, se requiere elección explícita. Los mensajes indican si faltan recursos activos y permiten revisar sus catálogos. El consultorio continúa siendo una selección explícita y obligatoria.

Los turnos mensuales actuales conservan las validaciones de especialidad, consultorio, cruces y carga aplicadas por el backend. Las políticas configurables, los turnos nocturnos y la aprobación mensual requieren incrementos posteriores.

## Ejemplo de separación

El panel operativo sigue `Inicio.jsx -> useResumenInicio.js -> resumenInicio.js -> servicios API`. La vista presenta datos; el hook controla estados, cancelación y reintento; el servicio carga profesionales, consultorios y la semana actual en tres consultas paralelas. El backend calcula la semana en la zona institucional. Las reglas definitivas de acceso y programación siguen aplicándose en el servidor.
