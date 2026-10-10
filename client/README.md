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

## Ejemplo de separación

El panel operativo sigue `Inicio.jsx -> useResumenInicio.js -> resumenInicio.js -> servicios API`. La vista presenta datos; el hook controla estados, cancelación y reintento; el servicio coordina las consultas y calcula la semana en la zona horaria institucional. Las reglas definitivas de acceso y programación siguen aplicándose en el servidor.
