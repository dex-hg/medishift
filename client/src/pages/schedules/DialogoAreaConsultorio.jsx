import { useState } from 'react';
import VentanaPlanificador from './VentanaPlanificador';
import SelectorEspecialidad from '../../components/SelectorEspecialidad';

export default function DialogoAreaConsultorio({ accion, datos, ocupado: ocupadoServidor, error, errores, guardar, cerrar, recargar }) {
  const [formulario, setFormulario] = useState({ idArea: accion.idArea || '', idConsultorio: '', codigo: '', nombre: '', ubicacion: '',
    idEspecialidad: datos.areas.find((area) => area.id === accion.idArea)?.idEspecialidad || '' });
  const [creandoEspecialidad, setCreandoEspecialidad] = useState(false);
  const ocupado = ocupadoServidor || creandoEspecialidad;
  const area = accion.tipo === 'area';
  const configurar = accion.tipo === 'especialidad-area';
  const vincular = accion.tipo === 'vincular';
  const cambiar = (evento) => setFormulario((anterior) => ({ ...anterior, [evento.target.name]: evento.target.value }));
  const enviar = (evento) => { evento.preventDefault(); guardar(formulario); };
  const pendientes = datos.consultorios.filter((registro) => !registro.idArea);
  return <VentanaPlanificador titulo={configurar ? 'Especialidad del área' : area ? 'Nueva área o servicio' : vincular ? 'Organizar consultorios' : 'Nuevo consultorio'}
    descripcion={configurar ? `Define qué especialidad admite ${datos.areas.find((registro) => registro.id === formulario.idArea)?.nombre}.`
      : area ? 'Crea una carpeta para agrupar consultorios, como Dermatología u Odontología.'
      : vincular ? 'Asocia un consultorio existente con su área.' : 'El consultorio quedará guardado dentro de esta área.'} ocupado={ocupado} cerrar={cerrar}>
    <form onSubmit={enviar} className="formulario-planificador">
      {error && <div className="planificador-error" role="alert"><p>{error}</p>
        <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={recargar}>Actualizar sin cerrar el formulario</button></div>}
      <fieldset className="formulario-planificador__campos" disabled={ocupado}>
        {!area && !configurar && <label className="campo-planificador"><span>Área o servicio</span><select name="idArea" required value={formulario.idArea} onChange={cambiar}>
          <option value="">Selecciona un área</option>{datos.areas.filter((registro) => registro.estado === 'Activo' && registro.idEspecialidad).map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre}</option>)}</select>
          {errores.idArea && <small role="alert">{errores.idArea}</small>}</label>}
        {configurar ? null : vincular ? <label className="campo-planificador"><span>Consultorio existente</span><select name="idConsultorio" required value={formulario.idConsultorio} onChange={cambiar}>
          <option value="">Selecciona un consultorio</option>{pendientes.map((registro) => <option key={registro.id} value={registro.id}>{registro.nombre} · {registro.codigo}</option>)}</select>
          {errores.idConsultorio && <small role="alert">{errores.idConsultorio}</small>}</label> : <>
          <div className="formulario-planificador__fila"><label className="campo-planificador"><span>Código {area ? 'del área' : 'único del consultorio'}</span>
            <input name="codigo" required maxLength={area ? 32 : 20} placeholder={area ? 'DER' : 'DER-01'} value={formulario.codigo} onChange={cambiar} />
            {errores.codigo && <small role="alert">{errores.codigo}</small>}</label>
          <label className="campo-planificador"><span>Nombre</span><input name="nombre" required maxLength={area ? 140 : 100}
            placeholder={area ? 'Dermatología' : 'Consultorio 1'} value={formulario.nombre} onChange={cambiar} />
            {errores.nombre && <small role="alert">{errores.nombre}</small>}</label></div>
          {!area && <label className="campo-planificador"><span>Ubicación</span><input name="ubicacion" required maxLength={100}
            placeholder="Primer piso, ala norte" value={formulario.ubicacion} onChange={cambiar} />{errores.ubicacion && <small role="alert">{errores.ubicacion}</small>}</label>}
          {!area && <p className="planificador-ayuda">El consultorio solo admitirá la especialidad seleccionada para su área.</p>}
        </>}
        {(area || configurar) && <SelectorEspecialidad etiqueta="Especialidad autorizada del área" name="idEspecialidad" usarId
          value={formulario.idEspecialidad} onChange={cambiar} especialidades={datos.especialidades}
          disabled={ocupadoServidor} error={errores.idEspecialidad} alOcupar={setCreandoEspecialidad}
          ayuda="Solo los profesionales que tengan esta especialidad podrán recibir turnos en el área." />}
      </fieldset>
      <div className="ventana-planificador__acciones"><button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={cerrar}>Cancelar</button>
        <button className="boton boton--primario" type="submit" disabled={ocupado}>{ocupado ? 'Guardando…' : configurar ? 'Guardar especialidad' : vincular ? 'Guardar asociación' : area ? 'Crear área' : 'Crear consultorio'}</button></div>
    </form>
  </VentanaPlanificador>;
}
