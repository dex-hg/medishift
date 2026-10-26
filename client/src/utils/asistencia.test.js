import test from 'node:test';
import assert from 'node:assert/strict';
import { filtrarTurnosAsistencia, minutosComputables, resumirAsistencia } from './asistencia.js';

const turnos = () => [
  { id: 'uno', idProfesional: 'medico', profesional: 'María Núñez', fecha: '2032-11-02', idArea: 'area-uno',
    idConsultorio: 'sala-uno', horaInicio: '08:00', horaFin: '10:00', estado: 'Borrador', asistencia: 'Falta' },
  { id: 'dos', idProfesional: 'medico', profesional: 'María Núñez', fecha: '2032-11-02', idArea: 'area-dos',
    idConsultorio: 'sala-dos', horaInicio: '10:00', horaFin: '12:30', estado: 'Borrador', asistencia: 'Asistencia' },
  { id: 'tres', idProfesional: 'otro', profesional: 'José Díaz', fecha: '2032-11-03', idArea: 'area-uno',
    idConsultorio: 'sala-uno', horaInicio: '08:00', horaFin: '10:00', estado: 'Aprobado', asistencia: 'Asistencia' },
];

test('la falta afecta solo al turno elegido y el otro consultorio del mismo médico mantiene sus horas', () => {
  const registros = turnos().filter((turno) => turno.idProfesional === 'medico');
  assert.equal(minutosComputables(registros[0]), 0);
  assert.equal(minutosComputables(registros[1]), 150);
  assert.deepEqual(resumirAsistencia(registros), { cantidad: 2, faltas: 1, minutos: 150 });
  registros[0].asistencia = 'Asistencia';
  assert.equal(resumirAsistencia(registros).minutos, 270);
});

test('búsqueda por palabras encuentra nombres sin distinguir tildes, orden o mayúsculas', () => {
  assert.deepEqual(filtrarTurnosAsistencia(turnos(), { busqueda: ' NUNEZ   maria ' }).map((turno) => turno.id), ['uno', 'dos']);
  assert.equal(filtrarTurnosAsistencia(turnos(), { busqueda: 'María Díaz' }).length, 0);
});

test('fecha, área, consultorio y estado se combinan sin incluir turnos de otros filtros', () => {
  assert.deepEqual(filtrarTurnosAsistencia(turnos(), { busqueda: 'maria', fecha: '2032-11-02',
    idArea: 'area-uno', idConsultorio: 'sala-uno', estado: 'Falta' }).map((turno) => turno.id), ['uno']);
  assert.equal(filtrarTurnosAsistencia(turnos(), { idArea: 'area-dos', idConsultorio: 'sala-uno' }).length, 0);
  assert.deepEqual(resumirAsistencia([]), { cantidad: 0, faltas: 0, minutos: 0 });
});

test('turnos cancelados, sustituidos y duraciones inválidas no aportan horas', () => {
  for (const cambio of [{ estado: 'Cancelado' }, { estado: 'Sustituido' }, { horaFin: '07:00' }, { horaFin: 'incorrecta' }]) {
    assert.equal(minutosComputables({ ...turnos()[1], ...cambio }), 0);
  }
  assert.equal(minutosComputables(null), 0);
});
