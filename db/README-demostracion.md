# Datos demostrativos de MediShift

## Clínicas con horarios y citas

Esta carga crea dos clínicas ficticias con datos para mostrar Horarios, Profesionales, Pacientes, Agenda de citas y el portal del paciente.

| Institución                                   | Zona           | Administrador                   | Médica                          |
| ---------------------------------------------- | -------------- | ------------------------------- | -------------------------------- |
| DEMO-MEDISHIFT-PE · Clínica Los Sauces       | America/Lima   | admin.pe@medishift.example.test | doctor.pe@medishift.example.test |
| DEMO-MEDISHIFT-CO · Centro Médico La Alameda | America/Bogota | admin.co@medishift.example.test | doctor.co@medishift.example.test |

Con PostgreSQL iniciado y las migraciones V001 a V008 aplicadas, ejecuta el siguiente comando desde la raíz del repositorio.

```powershell
python -B scripts/cargar_demostracion_clinica.py
```

El comando se conecta al PostgreSQL local de `server/.env`, o al indicado mediante las variables de entorno de MediShift, y utiliza `psql`. Si acabas de instalar MediShift, prepara primero el esquema y las migraciones siguiendo la [guía de instalación](migraciones/README.md). El comando imprime los meses creados y la ruta del archivo local de accesos. Para la base local predeterminada, las contraseñas nuevas están en `server/accesos-demostracion.local`, excluido de Git. Cada cuenta recibe una contraseña propia de esa instalación.

Cada clínica tiene **8 áreas principales, 1 subárea, 16 consultorios, 32 médicos y 240 pacientes ficticios**. Los turnos cubren días de atención a futuro y los tres meses completos siguientes. Las áreas atienden de lunes a viernes. Medicina general y Pediatría abren también algunos sábados. La agenda reserva citas en parte de los turnos aprobados y conserva espacios disponibles. Hay faltas registradas para mostrar su efecto en la asistencia.

En Horarios, abre **Cirugía plástica ambulatoria › Evaluación preoperatoria**. La subárea acepta Dermatología y Cirugía plástica. **PRE-01 · Evaluación clínica** admite ambas etiquetas. **PRE-02 · Evaluación quirúrgica** admite Cirugía plástica. La médica de demostración y otro profesional tienen las dos etiquetas. En el último mes, los planes de Dermatología, Cirugía plástica ambulatoria y Evaluación preoperatoria están en borrador para practicar el movimiento de turnos entre ellos.

Para la carga local de noviembre de 2026, usa **diciembre de 2026** para mostrar la agenda de citas y **febrero de 2027** para mover turnos entre borradores. La médica puede consultar sus citas y editar sus turnos futuros en borrador con su propia cuenta.

| Portal del paciente | DNI ficticio | Fecha de nacimiento |
| ------------------- | ------------ | ------------------- |
| Perú               | 00090101     | 14/05/1990          |
| Colombia            | 00091101     | 14/05/1990          |

Cada ficha de referencia tiene al menos una cita en Dermatología por mes aprobado de la carga inicial. Ingresa por **Portal del paciente** y elige uno de esos meses. Los DNI y los nombres se generan localmente con fines de demostración. El script comprueba el rango de DNI contra las fichas existentes al crear cada clínica y no llama a servicios de identidad.

La carga es transaccional. Al ejecutarla de nuevo en el mismo mes conserva las ediciones en planes ya preparados, sus citas y los accesos. En otro mes añade los periodos nuevos que falten y respeta los estados de planes existentes. Los meses ya preparados quedan registrados en auditoría para evitar su recreación. Si un código institucional coincide con una institución ajena o las políticas vigentes de la clínica producirían un conflicto, PostgreSQL revierte la carga. En Horarios, el calendario de feriados se consulta por separado a través de la aplicación.
