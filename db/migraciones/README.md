# Migraciones del núcleo mensual

V001 añade unidades y cabeceras de mes completo conservando los horarios anteriores. La línea base `db/medishift_postgresql.sql` no se vuelve a ejecutar sobre una base existente. Spring no aplica migraciones al arrancar.

Desde la raíz del repositorio, `python -B scripts/administrar_migracion_mensual.py estado` consulta el registro; `ensayar` respalda y restaura una copia temporal; `aplicar` exige ese ensayo correcto antes de aplicar V001 en una transacción y registrar su SHA-256. Los respaldos quedan en `server/target/respaldos`, ignorados por Git.

U001 solo permite retirar la ampliación si no existen unidades ni planes mensuales. Una aplicación registrada no debe revertirse fuera del procedimiento controlado: el ledger también debe conservar su coherencia. Una vez utilizados los planes, preparar una migración correctiva que preserve datos.

El procedimiento completo, los contratos, la evidencia y los pendientes están en el [plan central de Avance 3](<C:/Users/DEXTRE/OneDrive/Documentos/.Universidad/Ciclo VI/Curso Integrador I Sistemas - Software/Avance 3/plan/PLAN-CONTINUACION-MEDISHIFT.md>).
