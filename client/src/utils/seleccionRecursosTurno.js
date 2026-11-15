export function etiquetasDelRecurso(recurso) {
  return recurso?.etiquetas ?? recurso?.especialidades ?? [];
}

export function consultorioAdmiteEtiqueta(consultorio, idEtiqueta) {
  if (!consultorio || !idEtiqueta) return false;
  if (consultorio.idArea && !consultorio.etiquetas && 'idEspecialidadArea' in consultorio
    && consultorio.idEspecialidadArea !== idEtiqueta) return false;
  return consultorio.usoGeneral || etiquetasDelRecurso(consultorio).some((etiqueta) => etiqueta.id === idEtiqueta);
}

/** Opciones del formulario antes de validar la asignación en el servidor. */
export function especialidadesProfesional(recursos, idProfesional) {
  const profesional = recursos?.profesionales?.find((registro) => registro.id === idProfesional
    && registro.estado === 'Activo');
  return etiquetasDelRecurso(profesional);
}

export function consultoriosCompatibles(recursos, idEspecialidad) {
  if (!idEspecialidad) return [];
  return (recursos?.consultorios || []).filter((consultorio) => consultorio.estado === 'Activo'
    && consultorioAdmiteEtiqueta(consultorio, idEspecialidad));
}

function conservarConsultorio(recursos, idEspecialidad, idConsultorio) {
  return consultoriosCompatibles(recursos, idEspecialidad).some((consultorio) => consultorio.id === idConsultorio)
    ? idConsultorio : '';
}

/** Selecciona la especialidad única y conserva elecciones todavía compatibles. */
export function seleccionarProfesionalTurno(datos, idProfesional, recursos) {
  const especialidades = especialidadesProfesional(recursos, idProfesional);
  const idEspecialidad = especialidades.some((especialidad) => especialidad.id === datos.idEspecialidad)
    ? datos.idEspecialidad : especialidades.length === 1 ? especialidades[0].id : '';
  return { idEspecialidad,
    idConsultorio: conservarConsultorio(recursos, idEspecialidad, datos.idConsultorio) };
}

export function seleccionarEspecialidadTurno(datos, idEspecialidad, recursos) {
  const especialidades = especialidadesProfesional(recursos, datos.idProfesional);
  const especialidadValida = especialidades.some((especialidad) => especialidad.id === idEspecialidad)
    ? idEspecialidad : '';
  return { idEspecialidad: especialidadValida,
    idConsultorio: conservarConsultorio(recursos, especialidadValida, datos.idConsultorio) };
}
