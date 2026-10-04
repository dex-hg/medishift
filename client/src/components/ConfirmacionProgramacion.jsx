function ConfirmacionProgramacion({ titulo, descripcion, confirmar, cancelar, ocupado }) {
  return <section className="seccion-formulario" aria-label={titulo}>
    <div className="seccion-formulario__titulo"><div><h2>{titulo}</h2><p>{descripcion}</p></div></div>
    <div className="acciones-tabla">
      <button className="boton boton--fantasma" type="button" disabled={ocupado} onClick={cancelar}>Volver</button>
      <button className="boton boton--primario" type="button" disabled={ocupado} onClick={confirmar}>
        {ocupado ? 'Procesando…' : 'Confirmar'}
      </button>
    </div>
  </section>;
}

export default ConfirmacionProgramacion;
