import test from 'node:test';
import assert from 'node:assert/strict';
import { construirReporteMensual, duracionReporte, nombreArchivoReporte } from './reportesMensuales.js';
import { calendarioPrueba, identificadorPrueba, opcionesPrueba } from '../reportes/datosReportePrueba.js';

test('ficha individual incluye todos sus consultorios y conserva nombre, colegiatura y minutos exactos', () => {
  const datos = calendarioPrueba();
  const original = structuredClone(datos);
  const reporte = construirReporteMensual(datos, opcionesPrueba());
  assert.equal(reporte.profesional.nombre, 'María Núñez de la Peña');
  assert.equal(reporte.profesional.colegiatura, '001234');
  assert.equal(reporte.turnos.length, 3);
  assert.equal(new Set(reporte.turnos.map((turno) => turno.idConsultorio)).size, 2);
  assert.equal(reporte.minutos, 690);
  assert.equal(reporte.diasTrabajados, 2);
  assert.equal(reporte.totalesDiarios[0], 360);
  assert.equal(reporte.totalesDiarios[2], 330);
  assert.equal(reporte.totalesDiarios[1], 0);
  assert.deepEqual(reporte.estados, ['Borrador', 'Pendiente']);
  assert.equal(reporte.matriz.find((fila) => fila.franja === '06:00 - 09:30').minutos[2], 210);
  assert.deepEqual(datos, original);
});

test('consultorio visible y todo el mes aplican alcances distintos sin contar turnos cancelados o sustituidos', () => {
  const datos = calendarioPrueba();
  const consultorio = construirReporteMensual(datos, opcionesPrueba('consultorio'));
  const general = construirReporteMensual(datos, opcionesPrueba('mes'));
  assert.equal(consultorio.turnos.length, 3);
  assert.ok(consultorio.turnos.every((turno) => turno.idConsultorio === identificadorPrueba(11)));
  assert.equal(general.turnos.length, 4);
  assert.equal(general.minutos, 1050);
  assert.equal(general.profesional, null);
  assert.deepEqual(general.estados, ['Aprobado', 'Borrador', 'Pendiente']);
});

test('la matriz usa la cantidad real de días y reconoce febrero bisiesto', () => {
  for (const [periodo, cantidad] of [['2026-02', 28], ['2028-02', 29], ['2026-04', 30], ['2026-10', 31]]) {
    const reporte = construirReporteMensual(calendarioPrueba(periodo), opcionesPrueba('profesional', periodo));
    assert.equal(reporte.dias.length, cantidad);
    assert.equal(reporte.matriz[0].minutos.length, cantidad);
    assert.equal(reporte.dias.at(-1), `${periodo}-${cantidad}`);
  }
});

test('un profesional inactivo sin turnos produce una ficha vacía identificada', () => {
  const reporte = construirReporteMensual(calendarioPrueba(), { ...opcionesPrueba(), idProfesional: identificadorPrueba(3) });
  assert.equal(reporte.profesional.nombre, 'Profesional sin turnos');
  assert.equal(reporte.profesional.colegiatura, '');
  assert.equal(reporte.minutos, 0);
  assert.equal(reporte.diasTrabajados, 0);
  assert.equal(reporte.matriz.length, 0);
});

test('rechaza periodos, filtros, fechas y relaciones inválidos antes de generar un archivo', () => {
  for (const cambio of [{ periodo: '2026-13' }, { alcance: 'otro' }, { institucion: '' },
    { idProfesional: identificadorPrueba(999) }, { emitido: new Date('inválido') }]) {
    assert.throws(() => construirReporteMensual(calendarioPrueba(), { ...opcionesPrueba(), ...cambio }));
  }
  for (const cambio of [{ fecha: '2026-11-01' }, { horaFin: '05:00' }, { fecha: '2026-02-30' },
    { idConsultorio: identificadorPrueba(999) }, { horaInicio: '24:00' }]) {
    const datos = calendarioPrueba();
    Object.assign(datos.turnos[0], cambio);
    assert.throws(() => construirReporteMensual(datos, opcionesPrueba()), /inconsistentes/);
  }
  const duplicados = calendarioPrueba();
  duplicados.turnos.push(duplicados.turnos[0]);
  assert.throws(() => construirReporteMensual(duplicados, opcionesPrueba()), /incompletos/);
});

test('nombres de archivo son seguros y las duraciones no pierden minutos', () => {
  const reporte = construirReporteMensual(calendarioPrueba(), opcionesPrueba());
  assert.equal(nombreArchivoReporte(reporte, 'pdf'), 'horario-2026-10-maria-nunez-de-la-pena-001234.pdf');
  assert.equal(duracionReporte(365), '6 h 5 min');
  assert.equal(duracionReporte(0), '0 h');
  assert.throws(() => nombreArchivoReporte(reporte, 'csv'), /PDF o Excel/);
  reporte.profesional.nombre = '../../CON: prueba?*';
  assert.match(nombreArchivoReporte(reporte, 'xlsx'), /^horario-2026-10-con-prueba-001234\.xlsx$/);
});
