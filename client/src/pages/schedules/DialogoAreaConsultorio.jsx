import { useState } from 'react';
import VentanaPlanificador from './VentanaPlanificador';
import SelectorEtiquetas from '../../components/SelectorEtiquetas';
import { etiquetasDelArea, nombreUnidad } from '../../utils/calendarioMensual';

function Campo({ nombre, etiqueta, errores, children }) {
  return <label className="campo-planificador"><span>{etiqueta}</span>{children}
    {errores[nombre] && <small className="campo-planificador__error" role="alert">{errores[nombre]}</small>}
  </label>;
}

export default function DialogoAreaConsultorio({ accion, datos, ocupado: ocupadoServidor, error, errores, guardar, cerrar, recargar }) {
  const actual = datos.areas.find((area) => area.id === accion.idArea);
  const editar = accion.tipo === 'editar-area';
  const esArea = accion.tipo === 'area' || editar;
  const vinculacion = accion.tipo === 'vincular';
  const [creando, cambiarCreando] = useState(false);
  const [formulario, cambiarFormulario] = useState(() => ({
    idArea: accion.idArea || '', idAreaPadre: (editar ? actual?.idAreaPadre : accion.idAreaPadre) || '',
    idConsultorio: '', codigo: editar ? actual?.codigo || '' : '', nombre: editar ? actual?.nombre || '' : '',
    ubicacion: '', estado: editar ? actual?.estado || 'Activo' : 'Activo',
    idsEtiquetas: editar ? etiquetasDelArea(actual).map((etiqueta) => etiqueta.id) : [], revision: actual?.revision,
  }));
  const ocupado = ocupadoServidor || creando;
  const desactualizado = editar && (!actual || actual.revision !== formulario.revision);
  const padre = datos.areas.find((area) => area.id === formulario.idAreaPadre);
  const subareas = editar && datos.areas.some((area) => area.idAreaPadre === actual?.id);
  const posiblesPadres = datos.areas.filter((area) => !area.idAreaPadre && area.id !== actual?.id
    && (area.estado === 'Activo' || area.id === formulario.idAreaPadre));
  const organiza = datos.areas.filter((area) => area.estado === 'Activo' && etiquetasDelArea(area).length);
  const etiquetas = esArea ? datos.especialidades : etiquetasDelArea(actual && actual.id === formulario.idArea
    ? actual : datos.areas.find((area) => area.id === formulario.idArea));
  const titulo = editar ? 'Configurar área o subárea' : esArea ? (padre ? 'Nueva subárea' : 'Nueva área')
    : vinculacion ? 'Vincular consultorio' : 'Nuevo consultorio';
  const asignar = (campo, valor) => cambiarFormulario((anterior) => ({ ...anterior, [campo]: valor }));
  const cambiar = (evento) => asignar(evento.target.name, evento.target.value);
  const enviar = (evento) => {
    evento.preventDefault();
    if (!ocupado && !desactualizado) guardar(formulario);
  };

  return <VentanaPlanificador titulo={titulo} descripcion={editar ? actual?.nombre : padre?.nombre}
    ocupado={ocupado} cerrar={cerrar}>
    <form onSubmit={enviar} className="formulario-planificador">
      {error && <div className="planificador-error" role="alert"><p>{error}</p>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={recargar}>Actualizar calendario</button></div>}
      {desactualizado && <div className="planificador-error" role="alert"><p>La configuración del área cambió. Revisa tus entradas antes de guardarlas con la revisión actual.</p>
        {actual && <button type="button" className="boton boton--fantasma" onClick={() => asignar('revision', actual.revision)}>Usar revisión actual</button>}</div>}
      <fieldset className="formulario-planificador__campos" disabled={ocupado}>
        {!esArea && <Campo etiqueta="Área o subárea" nombre="idArea" errores={errores}>
          <select name="idArea" required value={formulario.idArea} onChange={(evento) => {
            cambiarFormulario((anterior) => ({ ...anterior, idArea: evento.target.value, idsEtiquetas: [] }));
          }}>
            <option value="">Selecciona una unidad</option>
            {organiza.map((area) => <option key={area.id} value={area.id}>{nombreUnidad(area, datos.areas)}</option>)}
          </select>
        </Campo>}
        {vinculacion ? <Campo etiqueta="Consultorio existente" nombre="idConsultorio" errores={errores}>
          <select name="idConsultorio" required value={formulario.idConsultorio} onChange={cambiar}>
            <option value="">Selecciona un consultorio</option>
            {datos.consultorios.filter((sala) => !sala.idArea).map((sala) =>
              <option key={sala.id} value={sala.id}>{sala.codigo} · {sala.nombre}</option>)}
          </select>
        </Campo> : <>
          <div className="formulario-planificador__fila">
            <Campo etiqueta="Código" nombre="codigo" errores={errores}><input name="codigo" required readOnly={editar}
              maxLength={esArea ? 32 : 20} value={formulario.codigo} onChange={cambiar} /></Campo>
            <Campo etiqueta="Nombre" nombre="nombre" errores={errores}><input name="nombre" required
              maxLength={esArea ? 140 : 100} value={formulario.nombre} onChange={cambiar} /></Campo>
          </div>
          {esArea ? <div className="formulario-planificador__fila">
            <Campo etiqueta="Área padre" nombre="idAreaPadre" errores={errores}>
              <select name="idAreaPadre" value={formulario.idAreaPadre} disabled={subareas} onChange={cambiar}>
                <option value="">Área principal</option>
                {posiblesPadres.map((area) => <option key={area.id} value={area.id}>{area.nombre}</option>)}
              </select>
            </Campo>
            {editar && <Campo etiqueta="Estado" nombre="estado" errores={errores}>
              <select name="estado" value={formulario.estado} onChange={cambiar}><option>Activo</option><option>Inactivo</option></select>
            </Campo>}
          </div> : <Campo etiqueta="Ubicación" nombre="ubicacion" errores={errores}>
            <input name="ubicacion" required maxLength={100} value={formulario.ubicacion} onChange={cambiar} />
          </Campo>}
          {esArea && !editar && padre && etiquetasDelArea(padre).length > 0 && <button className="boton boton--fantasma"
            type="button" onClick={() => asignar('idsEtiquetas', [...new Set([...formulario.idsEtiquetas, ...etiquetasDelArea(padre).map((etiqueta) => etiqueta.id)])])}>
            Copiar etiquetas de {padre.nombre}
          </button>}
          <SelectorEtiquetas etiqueta={esArea ? 'Etiquetas admitidas' : 'Restringir a estas etiquetas'} opciones={etiquetas}
            valor={formulario.idsEtiquetas} cambiar={(valor) => asignar('idsEtiquetas', valor)} error={errores.idsEtiquetas}
            permitirAlta={esArea} alOcupar={cambiarCreando}
            ayuda={esArea ? 'El médico debe tener una etiqueta en común. Puedes dejar el área pendiente de configurar.'
              : 'Si la lista queda vacía, el consultorio admite todas las etiquetas de su unidad.'} />
        </>}
      </fieldset>
      <div className="ventana-planificador__acciones">
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={cerrar}>Cancelar</button>
        <button className="boton boton--primario" type="submit" disabled={ocupado || desactualizado}>
          {ocupado ? 'Guardando…' : editar ? 'Guardar área' : vinculacion ? 'Guardar asociación' : esArea ? 'Crear unidad' : 'Crear consultorio'}
        </button>
      </div>
    </form>
  </VentanaPlanificador>;
}
