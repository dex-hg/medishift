import prueba from 'node:test';
import afirmar from 'node:assert/strict';
import { consultoriosCompatibles, especialidadesProfesional,
  seleccionarEspecialidadTurno, seleccionarProfesionalTurno } from './seleccionRecursosTurno.js';
import { validarTurno } from './validacionesProgramacion.js';

const PROFESIONAL = '10000000-0000-0000-0000-000000000001';
const OTRO_PROFESIONAL = '10000000-0000-0000-0000-000000000002';
const ESPECIALIDAD = '10000000-0000-0000-0000-000000000003';
const OTRA_ESPECIALIDAD = '10000000-0000-0000-0000-000000000004';
const CONSULTORIO = '10000000-0000-0000-0000-000000000005';
const GENERAL = '10000000-0000-0000-0000-000000000006';
const recursosPrueba = () => ({
  profesionales: [
    { id: PROFESIONAL, estado: 'Activo', especialidades: [{ id: ESPECIALIDAD }] },
    { id: OTRO_PROFESIONAL, estado: 'Activo', especialidades: [{ id: ESPECIALIDAD }, { id: OTRA_ESPECIALIDAD }] },
  ],
  consultorios: [
    { id: CONSULTORIO, estado: 'Activo', usoGeneral: false, especialidades: [{ id: ESPECIALIDAD }] },
    { id: GENERAL, estado: 'Activo', usoGeneral: true, especialidades: [] },
    { id: 'inactivo', estado: 'Inactivo', usoGeneral: true, especialidades: [] },
  ],
});
const seleccionVacia = () => ({ idProfesional: '', idEspecialidad: '', idConsultorio: '' });

prueba('la especialidad única permite ofrecer el consultorio al elegir profesional', () => {
  const recursos = recursosPrueba();
  const seleccion = seleccionarProfesionalTurno(seleccionVacia(), PROFESIONAL, recursos);
  afirmar.deepEqual(seleccion, { idEspecialidad: ESPECIALIDAD, idConsultorio: '' });
  afirmar.deepEqual(consultoriosCompatibles(recursos, seleccion.idEspecialidad).map((consultorio) => consultorio.id),
    [CONSULTORIO, GENERAL]);
});

prueba('varias especialidades requieren elección explícita sin asignar una sala', () => {
  const seleccion = seleccionarProfesionalTurno(seleccionVacia(), OTRO_PROFESIONAL, recursosPrueba());
  afirmar.deepEqual(seleccion, { idEspecialidad: '', idConsultorio: '' });
});

prueba('cambiar profesional conserva especialidad y consultorio que siguen siendo compatibles', () => {
  const datos = { idProfesional: PROFESIONAL, idEspecialidad: ESPECIALIDAD, idConsultorio: CONSULTORIO };
  afirmar.deepEqual(seleccionarProfesionalTurno(datos, OTRO_PROFESIONAL, recursosPrueba()),
    { idEspecialidad: ESPECIALIDAD, idConsultorio: CONSULTORIO });
  afirmar.equal(datos.idProfesional, PROFESIONAL);
});

prueba('cambiar especialidad elimina una sala incompatible y conserva una general', () => {
  const datos = { idProfesional: OTRO_PROFESIONAL, idEspecialidad: ESPECIALIDAD, idConsultorio: CONSULTORIO };
  afirmar.deepEqual(seleccionarEspecialidadTurno(datos, OTRA_ESPECIALIDAD, recursosPrueba()),
    { idEspecialidad: OTRA_ESPECIALIDAD, idConsultorio: '' });
  afirmar.deepEqual(seleccionarEspecialidadTurno({ ...datos, idConsultorio: GENERAL }, OTRA_ESPECIALIDAD, recursosPrueba()),
    { idEspecialidad: OTRA_ESPECIALIDAD, idConsultorio: GENERAL });
});

prueba('eliminar o desactivar al profesional elimina las selecciones dependientes', () => {
  const recursos = recursosPrueba();
  const datos = { idProfesional: PROFESIONAL, idEspecialidad: ESPECIALIDAD, idConsultorio: GENERAL };
  for (const identificador of ['', 'desconocido']) {
    afirmar.deepEqual(seleccionarProfesionalTurno(datos, identificador, recursos),
      { idEspecialidad: '', idConsultorio: '' });
  }
  recursos.profesionales[0].estado = 'Inactivo';
  afirmar.deepEqual(seleccionarProfesionalTurno(datos, PROFESIONAL, recursos),
    { idEspecialidad: '', idConsultorio: '' });
});

prueba('una sala inactiva no se conserva aunque admita la especialidad', () => {
  const recursos = recursosPrueba();
  recursos.consultorios[0].estado = 'Inactivo';
  afirmar.deepEqual(seleccionarProfesionalTurno({ ...seleccionVacia(), idConsultorio: CONSULTORIO }, PROFESIONAL, recursos),
    { idEspecialidad: ESPECIALIDAD, idConsultorio: '' });
});

prueba('sin especialidad o sin recursos no se ofrecen consultorios generales prematuramente', () => {
  afirmar.deepEqual(consultoriosCompatibles(recursosPrueba(), ''), []);
  afirmar.deepEqual(consultoriosCompatibles(null, ESPECIALIDAD), []);
  afirmar.deepEqual(especialidadesProfesional(null, PROFESIONAL), []);
});

prueba('una especialidad ajena al profesional no habilita una sala general', () => {
  const datos = { idProfesional: PROFESIONAL, idEspecialidad: ESPECIALIDAD, idConsultorio: GENERAL };
  afirmar.deepEqual(seleccionarEspecialidadTurno(datos, OTRA_ESPECIALIDAD, recursosPrueba()),
    { idEspecialidad: '', idConsultorio: '' });
});

prueba('las opciones no eluden la validación de consultorio obligatorio', () => {
  const recursos = recursosPrueba();
  const seleccion = seleccionarProfesionalTurno(seleccionVacia(), PROFESIONAL, recursos);
  const turno = { ...seleccion, idProfesional: PROFESIONAL, fecha: '2026-10-11',
    horaInicio: '08:00', horaFin: '11:00', observacion: '' };
  afirmar.ok(validarTurno(turno, { recursos }).idConsultorio);
  afirmar.deepEqual(validarTurno({ ...turno, idConsultorio: CONSULTORIO }, { recursos }), {});
});
