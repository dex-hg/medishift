# Migraciones del núcleo mensual

V001 añade unidades y cabeceras de mes completo conservando los horarios anteriores. La línea base `db/medishift_postgresql.sql` no se vuelve a ejecutar sobre una base existente. Spring no aplica migraciones al arrancar.

Desde la raíz del repositorio, `python -B scripts/administrar_migracion_mensual.py estado` consulta el registro; `ensayar` respalda y restaura una copia temporal; `aplicar` exige ese ensayo correcto antes de aplicar V001 en una transacción y registrar su SHA-256. Los respaldos quedan en `server/target/respaldos`, ignorados por Git.

U001 solo permite retirar la ampliación si no existen unidades ni planes mensuales. Una aplicación registrada no debe revertirse fuera del procedimiento controlado: el ledger también debe conservar su coherencia. Una vez utilizados los planes, preparar una migración correctiva que preserve datos.

V002 vincula consultorios con áreas mediante una relación limitada por institución. Se administra con `python -B scripts/administrar_consultorios_por_area.py estado|ensayar|aplicar` y requiere V001 verificada.

V003 añade el propietario opcional de `specialty` y la especialidad opcional de `planning_unit`. Se administra con `python -B scripts/administrar_especialidades_por_area.py estado|ensayar|aplicar` y exige V002 aplicada. `aplicar` respalda, restaura una copia aislada, ensaya la migración, compara las filas anteriores excluyendo solo las dos columnas nuevas y registra versión y SHA-256 en una transacción. La copia temporal se elimina al finalizar; el respaldo privado permanece en `server/target/respaldos`.

Las especialidades antiguas conservan propietario nulo y siguen compartidas; las nuevas pertenecen a la institución de la sesión. Las áreas antiguas conservan especialidad nula para su configuración explícita en Horarios. No se infiere a partir del nombre ni se modifican asignaciones. U003 rechaza retirar las columnas si ya existen especialidades propias o áreas configuradas. Con datos en uso, preparar una corrección aditiva y conservar el registro de migraciones.

El procedimiento completo, los contratos, la evidencia y los pendientes están en el [plan central de Avance 3](<C:/Users/DEXTRE/OneDrive/Documentos/.Universidad/Ciclo VI/Curso Integrador I Sistemas - Software/Avance 3/plan/PLAN-CONTINUACION-MEDISHIFT.md>).
