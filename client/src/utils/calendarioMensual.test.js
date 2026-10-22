import test from 'node:test';
import assert from 'node:assert/strict';
import { celdasDelMes, diasDelMes, desplazarMes, especialidadesCompatibles, leerArrastre, turnoInicial } from './calendarioMensual.js';

const ID = '10000000-0000-0000-0000-000000000001';
const OTRA = '10000000-0000-0000-0000-000000000002';

test('calendario alinea lunes, conserva todo el mes y respeta bisiestos', () => {
  const octubre = celdasDelMes('2026-10');
  assert.deepEqual(octubre.slice(0, 4), [null, null, null, '2026-10-01']);
  assert.equal(octubre.length % 7, 0);
  assert.equal(octubre.filter(Boolean).length, 31);
  assert.equal(diasDelMes('2028-02').at(-1), '2028-02-29');
  assert.equal(diasDelMes('2100-02').length, 28);
  assert.equal(diasDelMes('2000-02').length, 29);
  assert.deepEqual(celdasDelMes('2026-13'), []);
});

test('navegación mensual cruza años y no sale del rango admitido', () => {
  assert.equal(desplazarMes('2026-12', 1), '2027-01');
  assert.equal(desplazarMes('2026-01', -1), '2025-12');
  assert.equal(desplazarMes('9999-12', 1), '9999-12');
  assert.equal(desplazarMes('0001-01', -1), '0001-01');
  assert.equal(desplazarMes('incorrecto', 1), '');
});

test('arrastre acepta solo identificadores y tipos internos, descarta datos adicionales', () => {
  assert.deepEqual(leerArrastre(JSON.stringify({ tipo: 'profesional', id: ID, nombre: 'No confiar' })), { tipo: 'profesional', id: ID });
  assert.deepEqual(leerArrastre(JSON.stringify({ tipo: 'turno', id: ID })), { tipo: 'turno', id: ID });
  for (const contenido of ['', '{mal', 'null', JSON.stringify({ tipo: 'area', id: ID }), JSON.stringify({ tipo: 'turno', id: '../otro' }), 'x'.repeat(151)]) {
    assert.equal(leerArrastre(contenido), null);
  }
});

test('especialidad se autoselecciona solo cuando existe una compatible', () => {
  const profesional = { id: ID, especialidades: [{ id: ID, nombre: 'Dermatología' }, { id: OTRA, nombre: 'General' }] };
  const consultorio = { id: OTRA, idArea: ID, usoGeneral: false, especialidades: [{ id: ID, nombre: 'Dermatología' }] };
  assert.equal(especialidadesCompatibles(profesional, consultorio).length, 1);
  assert.equal(turnoInicial({ profesional, consultorio, fecha: '2026-10-26' }).idEspecialidad, ID);
  assert.equal(turnoInicial({ profesional, consultorio: { ...consultorio, usoGeneral: true }, fecha: '2026-10-26' }).idEspecialidad, '');
  assert.deepEqual(especialidadesCompatibles(profesional, null), []);
});

test('mover turno conserva las horas y la observación pero adapta fecha y consultorio', () => {
  const profesional = { id: ID, especialidades: [{ id: ID, nombre: 'Dermatología' }] };
  const consultorio = { id: OTRA, idArea: ID, usoGeneral: true, especialidades: [] };
  const datos = turnoInicial({ profesional, consultorio, fecha: '2026-10-27', turno: {
    horaInicio: '13:00', horaFin: '17:00', observacion: 'Nota existente', idEspecialidad: ID,
  } });
  assert.equal(datos.fecha, '2026-10-27');
  assert.equal(datos.idConsultorio, OTRA);
  assert.equal(datos.horaInicio, '13:00');
  assert.equal(datos.observacion, 'Nota existente');
});
