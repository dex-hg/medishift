import test from 'node:test';
import assert from 'node:assert/strict';
import {
  fechaValida, horaValida, lunesDeSemana, normalizarDisponibilidad, normalizarTurno,
  sumarDias, validarDisponibilidad, validarTurno,
} from './validacionesProgramacion.js';

const PROFESIONAL = '10000000-0000-0000-0000-000000000001';
const CONSULTORIO = '10000000-0000-0000-0000-000000000002';
const ESPECIALIDAD = '10000000-0000-0000-0000-000000000003';
const SEGUNDA = '10000000-0000-0000-0000-000000000004';
const turnoValido = () => ({ idProfesional: PROFESIONAL, idConsultorio: CONSULTORIO, idEspecialidad: ESPECIALIDAD,
  fecha: '2026-10-05', horaInicio: '08:00', horaFin: '14:00', observacion: '' });
const disponibilidadValida = () => ({ idProfesional: PROFESIONAL, diaSemana: '1', fechaInicio: '2026-10-05',
  fechaFin: '2026-10-11', horaInicio: '06:00', horaFin: '20:00', estado: 'Activo', observacion: '' });
const recursosValidos = () => ({ profesionales: [{ id: PROFESIONAL, estado: 'Activo', especialidades: [{ id: ESPECIALIDAD }, { id: SEGUNDA }] }],
  consultorios: [{ id: CONSULTORIO, estado: 'Activo', usoGeneral: true, especialidades: [] }] });

test('calendario ISO valida bisiestos y normaliza domingo a lunes sin depender de zona del equipo', () => {
  assert.equal(fechaValida('2024-02-29'), true);
  for (const invalida of ['2026-02-29', '2026-13-01', '2026-04-31', '0000-01-01', '05/10/2026']) assert.equal(fechaValida(invalida), false);
  assert.equal(lunesDeSemana('2026-10-11'), '2026-10-05');
  assert.equal(sumarDias('2026-12-31', 1), '2027-01-01');
  assert.equal(horaValida('23:59'), true);
  for (const invalida of ['24:00', '8:00', '08:60', '08:00:00']) assert.equal(horaValida(invalida), false);
});

test('la disponibilidad admite ventanas superiores a seis horas y convierte weekday del formulario', () => {
  assert.deepEqual(validarDisponibilidad(disponibilidadValida()), {});
  assert.equal(normalizarDisponibilidad(disponibilidadValida()).diaSemana, 1);
  assert.ok(validarDisponibilidad({ ...disponibilidadValida(), diaSemana: true }).diaSemana);
  assert.ok(validarDisponibilidad({ ...disponibilidadValida(), diaSemana: '8' }).diaSemana);
});

test('la vigencia debe contener al menos una ocurrencia del día seleccionado', () => {
  const datos = { ...disponibilidadValida(), fechaInicio: '2026-10-06', fechaFin: '2026-10-07' };
  assert.ok(validarDisponibilidad(datos).diaSemana);
  assert.deepEqual(validarDisponibilidad({ ...datos, diaSemana: '2' }), {});
  assert.ok(validarDisponibilidad({ ...datos, fechaFin: '2026-10-05' }).fechaFin);
});

test('las franjas y turnos no cruzan medianoche y el turno admite exactamente seis horas', () => {
  assert.deepEqual(validarTurno(turnoValido()), {});
  assert.ok(validarTurno({ ...turnoValido(), horaFin: '14:01' }).horaFin);
  assert.ok(validarTurno({ ...turnoValido(), horaInicio: '22:00', horaFin: '02:00' }).horaFin);
  assert.ok(validarDisponibilidad({ ...disponibilidadValida(), horaFin: '06:00' }).horaFin);
});

test('jornada diaria acumula turnos y excluye el turno que se está editando', () => {
  const existente = { ...turnoValido(), id: 'turno-existente', horaInicio: '15:00', horaFin: '16:00', estado: 'Borrador' };
  assert.ok(validarTurno(turnoValido(), { turnos: [existente] }).horaFin);
  assert.deepEqual(validarTurno(turnoValido(), { turnos: [existente], idActual: existente.id }), {});
});

test('jornada semanal admite exactamente treinta y seis horas y rechaza exceso', () => {
  const turnos = Array.from({ length: 5 }, (_, indice) => ({ ...turnoValido(), id: `t-${indice}`,
    fecha: sumarDias('2026-10-05', indice + 1), estado: 'Borrador' }));
  assert.deepEqual(validarTurno(turnoValido(), { turnos }), {});
  turnos.push({ ...turnoValido(), id: 't-domingo', fecha: '2026-10-11', horaFin: '09:00', estado: 'Borrador' });
  assert.ok(validarTurno(turnoValido(), { turnos }).fecha);
});

test('cruces de profesional y consultorio se rechazan pero las franjas contiguas son válidas', () => {
  const anterior = { ...turnoValido(), id: 'anterior', horaInicio: '07:00', horaFin: '08:00', estado: 'Borrador' };
  assert.deepEqual(validarTurno({ ...turnoValido(), horaFin: '13:00' }, { turnos: [anterior] }), {});
  anterior.horaFin = '09:00';
  const errores = validarTurno({ ...turnoValido(), horaFin: '10:00' }, { turnos: [anterior] });
  assert.ok(errores.idProfesional);
  assert.ok(errores.idConsultorio);
});

test('especialidades secundarias y consultorio general son compatibles', () => {
  const recursos = recursosValidos();
  assert.deepEqual(validarTurno({ ...turnoValido(), idEspecialidad: SEGUNDA }, { recursos }), {});
  recursos.consultorios[0].usoGeneral = false;
  recursos.consultorios[0].especialidades = [{ id: ESPECIALIDAD }];
  assert.ok(validarTurno({ ...turnoValido(), idEspecialidad: SEGUNDA }, { recursos }).idConsultorio);
  recursos.profesionales[0].estado = 'Inactivo';
  assert.ok(validarTurno(turnoValido(), { recursos }).idProfesional);
});

test('DTO omite identificadores de tenant, revisión y etiquetas de respuesta', () => {
  const bruto = { ...turnoValido(), idInstitucion: 'ajeno', id: 'registro', revision: 'revision', profesional: 'Nombre' };
  assert.deepEqual(normalizarTurno(bruto), turnoValido());
  assert.ok(!Object.hasOwn(normalizarDisponibilidad({ ...disponibilidadValida(), revision: 'r' }), 'revision'));
});

test('observaciones respetan límites, Unicode y los tipos de texto', () => {
  assert.deepEqual(validarTurno({ ...turnoValido(), observacion: '😀'.repeat(180) }), {});
  assert.ok(validarTurno({ ...turnoValido(), observacion: '😀'.repeat(181) }).observacion);
  assert.ok(validarTurno({ ...turnoValido(), observacion: '\ud800' }).observacion);
  assert.ok(validarDisponibilidad({ ...disponibilidadValida(), observacion: '\0' }).observacion);
  assert.ok(validarDisponibilidad({ ...disponibilidadValida(), horaInicio: 800 }).horaInicio);
});
