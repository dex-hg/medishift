/** Datos ficticios y aislados para comprobar los formatos de exportación. */
export const identificadorPrueba = (numero) => `10000000-0000-4000-8000-${String(numero).padStart(12, '0')}`;

export function calendarioPrueba(periodo = '2026-10') {
  const primerProfesional = identificadorPrueba(1);
  const otroProfesional = identificadorPrueba(2);
  const primeraSala = identificadorPrueba(11);
  const otraSala = identificadorPrueba(12);
  const area = identificadorPrueba(21);
  const otraArea = identificadorPrueba(22);
  const turno = (numero, persona, sala, dia, inicio, fin, estado = 'Borrador') => ({
    id: identificadorPrueba(numero), idPlan: identificadorPrueba(31), idArea: sala === primeraSala ? area : otraArea,
    idProfesional: persona, profesional: 'Nombre del turno', idConsultorio: sala, consultorio: 'Consultorio del turno',
    idEspecialidad: identificadorPrueba(41), especialidad: 'Medicina general', fecha: `${periodo}-${dia}`,
    horaInicio: inicio, horaFin: fin, estado, observacion: '', revision: 'b'.repeat(64),
  });
  return { zonaHoraria: 'America/Lima', areas: [{ id: area, nombre: 'Medicina general', codigo: 'MG', estado: 'Activo' },
    { id: otraArea, nombre: 'Atención externa', codigo: 'AE', estado: 'Activo' }],
  consultorios: [{ id: primeraSala, nombre: 'Consultorio 1', codigo: 'MG-01', idArea: area, estado: 'Activo' },
    { id: otraSala, nombre: 'Consultorio 2', codigo: 'AE-01', idArea: otraArea, estado: 'Activo' }],
  profesionales: [{ id: primerProfesional, nombre: 'María Núñez de la Peña', colegiatura: '001234', estado: 'Activo' },
    { id: otroProfesional, nombre: 'José Díaz', colegiatura: '006789', estado: 'Activo' },
    { id: identificadorPrueba(3), nombre: 'Profesional sin turnos', colegiatura: '', estado: 'Inactivo' }],
  planes: [], turnos: [turno(101, primerProfesional, primeraSala, '01', '06:00', '12:00'),
    turno(102, primerProfesional, otraSala, '03', '06:00', '09:30'),
    turno(103, primerProfesional, primeraSala, '03', '12:00', '14:00', 'Pendiente'),
    turno(104, otroProfesional, primeraSala, '01', '12:00', '18:00', 'Aprobado'),
    turno(105, primerProfesional, primeraSala, '05', '06:00', '12:00', 'Cancelado'),
    turno(106, primerProfesional, primeraSala, '06', '06:00', '12:00', 'Sustituido')] };
}

export const opcionesPrueba = (alcance = 'profesional', periodo = '2026-10') => ({ periodo, alcance,
  institucion: 'Institución de prueba MediShift', idProfesional: identificadorPrueba(1), idConsultorio: identificadorPrueba(11),
  emitido: new Date('2026-10-23T15:30:00Z') });
