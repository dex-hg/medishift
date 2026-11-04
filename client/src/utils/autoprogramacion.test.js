import test from 'node:test';
import assert from 'node:assert/strict';
import { consultorioOcupado, reservasDelDia, rutaPermitida } from './autoprogramacion.js';

const reserva = { idTurno: 'turno', idConsultorio: 'sala', inicio: '2032-11-02T13:00:00Z', fin: '2032-11-02T15:00:00Z' };
const formulario = { idConsultorio: 'sala', fecha: '2032-11-02', horaInicio: '09:00', horaFin: '11:00' };

test('rechaza cruces parciales, iguales y contenidos en el consultorio seleccionado', () => {
  for (const [horaInicio, horaFin] of [['08:00', '10:00'], ['09:00', '11:00'], ['07:00', '09:00'], ['08:30', '09:00'], ['07:00', '11:00']]) {
    assert.equal(consultorioOcupado({ ...formulario, horaInicio, horaFin }, [reserva], 'America/Lima'), true);
  }
});

test('permite terminar cuando comienza otra reserva y comenzar cuando termina', () => {
  for (const [horaInicio, horaFin] of [['06:00', '08:00'], ['10:00', '12:00']]) {
    assert.equal(consultorioOcupado({ ...formulario, horaInicio, horaFin }, [reserva], 'America/Lima'), false);
  }
});

test('no considera otros consultorios ni el turno que se está editando', () => {
  assert.equal(consultorioOcupado(formulario, [reserva], 'America/Lima', 'turno'), false);
  assert.equal(consultorioOcupado({ ...formulario, idConsultorio: 'otra' }, [reserva], 'America/Lima'), false);
  assert.equal(consultorioOcupado({ ...formulario, fecha: '2032-11-03' }, [reserva], 'America/Lima'), false);
});

test('muestra la ocupación en la zona institucional y recorta las reservas que cruzan medianoche', () => {
  const nocturna = { ...reserva, inicio: '2032-11-02T03:00:00Z', fin: '2032-11-02T07:00:00Z' };
  const primera = reservasDelDia([nocturna], 'sala', '2032-11-01', 'America/Lima');
  const segunda = reservasDelDia([nocturna], 'sala', '2032-11-02', 'America/Lima');
  assert.equal(primera[0].horaInicio, '22:00'); assert.equal(primera[0].horaFin, '24:00');
  assert.equal(segunda[0].horaInicio, '00:00'); assert.equal(segunda[0].horaFin, '02:00');
  assert.equal(reservasDelDia([nocturna], 'sala', '2032-11-03', 'America/Lima').length, 0);
});

test('maneja entradas parciales sin bloquear el formulario con falsos conflictos', () => {
  assert.equal(consultorioOcupado({ ...formulario, horaInicio: '' }, [reserva], 'America/Lima'), false);
  assert.equal(consultorioOcupado({ ...formulario, horaFin: '08:00' }, [reserva], 'America/Lima'), false);
  assert.deepEqual(reservasDelDia(null, 'sala', 'incorrecta', 'America/Lima'), []);
  assert.deepEqual(reservasDelDia([reserva], 'sala', '2032-11-02', 'Zona/Inexistente'), []);
});

test('la navegación del doctor limita rutas administrativas y las cuentas sin rol no reciben acceso', () => {
  const doctor = { esAdministrador: false, esDoctor: true };
  for (const ruta of ['/horarios', '/mi-perfil', '/configuraciones']) assert.equal(rutaPermitida(doctor, ruta), true);
  for (const ruta of ['/panel', '/profesionales', '/horarios/semanales', '/horarios/nuevo', '/faltas']) assert.equal(rutaPermitida(doctor, ruta), false);
  assert.equal(rutaPermitida({ esAdministrador: false, esDoctor: false }, '/horarios'), false);
  assert.equal(rutaPermitida({ esAdministrador: true }, '/profesionales'), true);
});
