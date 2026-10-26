import test from 'node:test';
import assert from 'node:assert/strict';
import { buscarFeriado, crearAbonosFeriados, horasProfesional, validarFeriados } from './feriados.js';
import { calendarioPrueba, identificadorPrueba } from '../reportes/datosReportePrueba.js';

function escenario() {
  const datos = calendarioPrueba('2026-11');
  datos.feriados.dias = [{ fecha: '2026-11-01', nombre: 'Feriado nacional de ensayo' },
    { fecha: '2026-11-08', nombre: 'Otro feriado de ensayo' }];
  return datos;
}

test('acredita seis horas por feriado aunque no haya turno ese día e incluye los domingos', () => {
  const datos = escenario();
  const resumen = horasProfesional(identificadorPrueba(1), datos.turnos, datos.feriados);
  assert.equal(new Date('2026-11-01T12:00:00Z').getUTCDay(), 0);
  assert.equal(resumen.minutosFeriados, 720);
  assert.equal(resumen.minutosTurnos, 330);
  assert.equal(resumen.minutos, 1050);
  assert.equal(buscarFeriado(datos.feriados, '2026-11-08').nombre, 'Otro feriado de ensayo');
});

test('varios turnos y consultorios no duplican el abono ni una falta en feriado lo anula', () => {
  const datos = escenario();
  datos.turnos[0].asistencia = 'Falta';
  datos.turnos.push({ ...datos.turnos[0], id: identificadorPrueba(150), idConsultorio: identificadorPrueba(12),
    idArea: identificadorPrueba(22), horaInicio: '14:00', horaFin: '16:00', asistencia: 'Asistencia' });
  const abonos = crearAbonosFeriados(datos.turnos, datos.feriados).filter((abono) => abono.idProfesional === identificadorPrueba(1));
  assert.equal(abonos.length, 2);
  assert.equal(horasProfesional(identificadorPrueba(1), datos.turnos, datos.feriados).minutos, 1050);
  assert.equal(horasProfesional(identificadorPrueba(3), datos.turnos, datos.feriados).minutos, 0);
});

test('solo los turnos cancelados o sustituidos no generan derecho al abono', () => {
  const datos = escenario();
  const turnos = datos.turnos.filter((turno) => ['Cancelado', 'Sustituido'].includes(turno.estado));
  assert.deepEqual(crearAbonosFeriados(turnos, datos.feriados), []);
  assert.equal(horasProfesional(identificadorPrueba(1), turnos, datos.feriados).minutos, 0);
});

test('valida fechas civiles del mes sin desplazarlas por UTC y rechaza duplicados o periodos ajenos', () => {
  const datos = escenario();
  assert.equal(validarFeriados(datos.feriados, '2026-11'), true);
  for (const dias of [[...datos.feriados.dias, datos.feriados.dias[0]], [{ fecha: '2026-12-01', nombre: 'Otro mes' }],
    [{ fecha: '2026-11-31', nombre: 'Imposible' }]]) assert.equal(validarFeriados({ ...datos.feriados, dias }, '2026-11'), false);
});
