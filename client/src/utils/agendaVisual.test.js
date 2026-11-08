import test from 'node:test';
import assert from 'node:assert/strict';
import { segmentosTurno, espaciosLibres, sumarMinutos, datosDelDia, erroresDisponibilidad } from './agendaVisual.js';

const turno = { id: 'turno', idProfesional: 'medico', inicio: '2032-11-02T13:00:00Z', fin: '2032-11-02T17:00:00Z' };
const cita = { id: 'cita', idProfesional: 'medico', estado: 'programada', inicio: '2032-11-02T14:00:00Z', fin: '2032-11-02T14:30:00Z' };
const ahora = Date.parse('2032-11-01T00:00:00Z');

test('divide el turno en intervalos libres y ocupados sin imponer duración de cita', () => {
  const segmentos = segmentosTurno(turno, [cita], { ahora });
  assert.deepEqual(segmentos.map((segmento) => segmento.tipo), ['libre', 'ocupado', 'libre']);
  assert.equal(segmentos[0].fin, Date.parse(cita.inicio));
  assert.equal(segmentos[2].inicio, Date.parse(cita.fin));
});
test('canceladas y citas de otro médico no ocupan; reprogramar libera el intervalo propio', () => {
  assert.equal(espaciosLibres(turno, [{ ...cita, estado: 'cancelada' }, { ...cita, idProfesional: 'otro' }], { ahora }).length, 1);
  assert.equal(espaciosLibres(turno, [cita], { ahora, excluir: cita.id })[0].fin, Date.parse(turno.fin));
});
test('intervalos consecutivos y reservas superpuestas no generan espacios falsos', () => {
  const segunda = { ...cita, id: 'segunda', inicio: cita.fin, fin: '2032-11-02T15:00:00Z' };
  assert.equal(espaciosLibres(turno, [cita, segunda], { ahora }).length, 2);
  const cubierto = { ...cita, inicio: '2032-11-02T12:00:00Z', fin: '2032-11-02T18:00:00Z' };
  assert.deepEqual(espaciosLibres(turno, [cubierto], { ahora }), []);
});
test('no ofrece reservar el pasado ni el minuto ya iniciado', () => {
  const resultado = espaciosLibres(turno, [], { ahora: Date.parse('2032-11-02T13:15:25Z') });
  assert.equal(resultado[0].inicio, Date.parse('2032-11-02T13:16:00Z'));
  assert.deepEqual(espaciosLibres(turno, [], { ahora: Date.parse(turno.fin) }), []);
});
test('agrupa por fecha institucional y médico sin usar el día UTC del equipo', () => {
  const noche = { ...turno, inicio: '2032-11-03T01:00:00Z' };
  const datos = { turnos: [noche], citas: [], zonaHoraria: 'America/Lima' };
  assert.equal(datosDelDia(datos, '2032-11-02').turnos.length, 1);
  assert.equal(datosDelDia(datos, '2032-11-03').turnos.length, 0);
  assert.equal(datosDelDia(datos, '2032-11-02', 'otro').turnos.length, 0);
});
test('horas libres se mantienen flexibles y rechazan cruces y salidas del turno', () => {
  const datos = { fecha: '2032-11-02', horaInicio: '08:00', horaFin: '08:17' };
  assert.deepEqual(erroresDisponibilidad(datos, turno, [cita], 'America/Lima'), {});
  assert.ok(erroresDisponibilidad({ ...datos, horaFin: '09:01' }, turno, [cita], 'America/Lima').horaInicio);
  assert.ok(erroresDisponibilidad({ ...datos, horaInicio: '07:59' }, turno, [], 'America/Lima').horaInicio);
  assert.equal(sumarMinutos('08:50', 30), '09:20');
  assert.equal(sumarMinutos('23:50', 15), '');
  assert.equal(sumarMinutos('25:00', 15), '');
});
