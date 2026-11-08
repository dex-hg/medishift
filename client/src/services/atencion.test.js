import test from 'node:test';
import assert from 'node:assert/strict';
import { guardarPaciente, buscarPacienteDni, obtenerAgenda, guardarCita, cambiarEstadoCita, solicitarAtencion } from './atencion.js';

const id = '11111111-1111-4111-8111-111111111111';
const otro = '22222222-2222-4222-8222-222222222222';
const revision = 'a'.repeat(64);
const paciente = { id, codigo: 'PAC-ENSAYO', nombres: 'Ana', apellidos: 'Pérez', dni: '00123456', fechaNacimiento: '2000-02-29', correo: '', telefono: '', activo: true, revision };
const cita = { id: otro, idPaciente: id, paciente: 'Ana Pérez', codigoPaciente: 'PAC-ENSAYO', idTurno: otro, idProfesional: otro,
  profesional: 'Doctor', idConsultorio: otro, consultorio: 'Sala', inicio: '2032-11-02T13:00:00Z', fin: '2032-11-02T13:17:00Z', estado: 'programada', revision };
const datosCita = { idPaciente: id, idTurno: otro, fecha: '2032-11-02', horaInicio: '08:00', horaFin: '08:17' };
const responder = (contenido, estado = 200) => async () => new Response(JSON.stringify(contenido), { status: estado, headers: { 'Content-Type': 'application/json' } });

test('crea ficha sin enviar institución, código ni campos clínicos y conserva ceros del DNI', async () => {
  let solicitud;
  const resultado = await guardarPaciente({ ...paciente, nombres: '  Ana  ', idInstitucion: otro, diagnostico: 'No solicitado' }, {
    ejecutarSolicitud: async (ruta, opciones) => { solicitud = { ruta, ...opciones }; return new Response(JSON.stringify(paciente), { status: 201 }); },
  });
  assert.equal(resultado.dni, '00123456'); assert.equal(solicitud.credentials, 'same-origin'); assert.equal(solicitud.cache, 'no-store');
  assert.deepEqual(JSON.parse(solicitud.body), { nombres: 'Ana', apellidos: 'Pérez', dni: '00123456', fechaNacimiento: '2000-02-29', correo: '', telefono: '', activo: true });
});
test('valida respuesta limitada de búsqueda y no exige datos privados', async () => {
  const resumen = { id, codigo: paciente.codigo, nombres: 'Ana', apellidos: 'Pérez', dni: '00123456', activo: true, puedeConsultar: false };
  const resultado = await buscarPacienteDni('00123456', { ejecutarSolicitud: responder([resumen]) });
  assert.equal(resultado[0].puedeConsultar, false); assert.equal(resultado[0].correo, undefined);
  assert.throws(() => buscarPacienteDni('123'), /8 dígitos/);
});
test('reprograma con If-Match y propaga cruces sin reintentar la reserva', async () => {
  let intentos = 0;
  await assert.rejects(guardarCita({ ...datosCita, idProfesional: id }, { id: otro, revision, ejecutarSolicitud: async (ruta, opciones) => {
    intentos++; assert.equal(opciones.headers['If-Match'], `"${revision}"`); assert.deepEqual(JSON.parse(opciones.body), datosCita);
    return new Response(JSON.stringify({ mensaje: 'Cruce de paciente', errores: { horaInicio: 'Ocupado' } }), { status: 409 });
  } }), (error) => error.estado === 409 && error.errores.horaInicio === 'Ocupado');
  assert.equal(intentos, 1);
});
test('rechaza respuestas de agenda malformadas y conserva las citas canceladas', async () => {
  const agenda = { fecha: '2032-11-02', zonaHoraria: 'America/Lima', turnos: [], citas: [{ ...cita, estado: 'cancelada' }] };
  assert.equal((await obtenerAgenda(agenda.fecha, { ejecutarSolicitud: responder(agenda) })).citas[0].estado, 'cancelada');
  await assert.rejects(obtenerAgenda(agenda.fecha, { ejecutarSolicitud: responder({ ...agenda, citas: [{ ...cita, estado: 'inventado' }] }) }), /no es válida/);
  assert.throws(() => obtenerAgenda('2032-02-30'), /fecha válida/);
});
test('no permite volver a programada y transmite cancelación con revisión', async () => {
  assert.throws(() => cambiarEstadoCita(id, 'programada'), /estado final/);
  const resultado = await cambiarEstadoCita(otro, 'cancelada', { revision, ejecutarSolicitud: responder({ ...cita, estado: 'cancelada' }) });
  assert.equal(resultado.estado, 'cancelada');
});
test('una espera vencida aborta y no confirma guardado', async () => {
  let senal;
  await assert.rejects(solicitarAtencion('/api/citas', 'POST', (valor) => valor, { datos: datosCita, tiempoEspera: 5,
    ejecutarSolicitud: async (ruta, opciones) => { senal = opciones.signal; return new Promise(() => {}); } }), /tardó demasiado/);
  assert.equal(senal.aborted, true);
});
