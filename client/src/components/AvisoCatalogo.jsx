import { AlertCircle, CheckCircle2, LoaderCircle, Trash2 } from 'lucide-react';

export function AvisoCatalogo({ cargando = false, error = '', mensaje = '', reintentar }) {
  if (cargando) return <div className="sin-resultados" role="status">
    <LoaderCircle size={28} aria-hidden="true" /><strong>Cargando registros…</strong>
  </div>;
  if (!error && !mensaje) return null;
  const Icono = error ? AlertCircle : CheckCircle2;
  return <div className={`aviso ${error ? 'aviso--restriccion' : 'aviso--resultado'}`} role={error ? 'alert' : 'status'}>
    <Icono size={18} aria-hidden="true" /><p>{error || mensaje}</p>
    {error && reintentar && <button className="boton boton--fantasma" type="button" onClick={reintentar}>Reintentar</button>}
  </div>;
}

export function ConfirmacionEliminar({ descripcion, eliminando, confirmar, cancelar }) {
  return <section className="seccion-formulario" aria-label="Confirmar eliminación">
    <div className="seccion-formulario__titulo"><Trash2 size={19} aria-hidden="true" /><div>
      <h2>Eliminar registro</h2>
      <p>Se eliminará {descripcion}. Esta acción no se puede deshacer.</p>
    </div></div>
    <div className="acciones-tabla">
      <button className="boton boton--fantasma" type="button" onClick={cancelar} disabled={eliminando}>Cancelar</button>
      <button className="boton boton--primario" type="button" onClick={confirmar} disabled={eliminando}>
        {eliminando ? 'Eliminando…' : 'Confirmar eliminación'}
      </button>
    </div>
  </section>;
}
