import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizarPaciente, validarPaciente, validarCita, partesLocales, accionesCita } from './validacionesAtencion.js';
import { rutaPermitida } from './autoprogramacion.js';

test('ficha valida DNI con ceros, nacimiento bisiesto y contacto opcional', () => {
  const datos = normalizarPaciente({ nombres: ' María  Ana ', apellidos: 'Pérez', dni: '00123456', fechaNacimiento: '2000-02-29', activo: true });
  assert.deepEqual(validarPaciente(datos, '2026-11-07'), {});
  assert.equal(datos.nombres, 'María Ana');
  assert.ok(validarPaciente({ ...datos, fechaNacimiento: '2027-01-01' }, '2026-11-07').fechaNacimiento);
  assert.ok(validarPaciente({ ...datos, dni: '1234567' }).dni);
  assert.ok(validarPaciente({ ...datos, nombres: '\uD800' }).nombres);
});
test('rechaza citas sin paciente y tiempos invertidos; admite duraciones libres', () => {
  const id = '11111111-1111-4111-8111-111111111111';
  const datos = { idPaciente: id, idTurno: id, fecha: '2032-11-02', horaInicio: '08:00', horaFin: '08:17' };
  assert.deepEqual(validarCita(datos), {});
  assert.ok(validarCita({ ...datos, horaFin: '08:00' }).horaFin);
  assert.ok(validarCita({ ...datos, idPaciente: '' }).idPaciente);
});
test('respeta horas institucionales sin usar la zona del equipo', () => {
  assert.deepEqual(partesLocales('2032-11-02T13:17:00Z', 'America/Lima'), { fecha: '2032-11-02', hora: '08:17' });
});
test('habilita estados por tiempo y mantiene los finales cerrados', () => {
  const cita = { estado: 'programada', inicio: '2032-11-02T13:00:00Z', fin: '2032-11-02T14:00:00Z' };
  assert.deepEqual(accionesCita(cita, Date.parse('2032-11-02T12:00:00Z')), ['reprogramar', 'cancelada']);
  assert.ok(accionesCita(cita, Date.parse('2032-11-02T13:30:00Z')).includes('atendida'));
  assert.ok(!accionesCita(cita, Date.parse('2032-11-02T13:30:00Z')).includes('no_asistio'));
  assert.ok(accionesCita(cita, Date.parse('2032-11-02T14:00:00Z')).includes('no_asistio'));
  assert.deepEqual(accionesCita({ ...cita, estado: 'atendida' }), []);
});
test('doctor accede a pacientes y agenda sin abrir catálogos administrativos', () => {
  const sesion = { esDoctor: true };
  for (const ruta of ['/pacientes', '/pacientes/nuevo', '/pacientes/11111111-1111-4111-8111-111111111111', '/citas']) assert.equal(rutaPermitida(sesion, ruta), true);
  for (const ruta of ['/profesionales', '/consultorios', '/horarios/semanales']) assert.equal(rutaPermitida(sesion, ruta), false);
  assert.equal(rutaPermitida({}, '/pacientes'), false);
});
