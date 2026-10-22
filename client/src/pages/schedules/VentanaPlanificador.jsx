import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';

export default function VentanaPlanificador({ titulo, descripcion, ocupado, cerrar, children }) {
  const referencia = useRef(null);
  const identificador = useId();
  useEffect(() => {
    const ventana = referencia.current;
    ventana.showModal();
    return () => ventana.close();
  }, []);
  return <dialog ref={referencia} className="ventana-planificador" aria-labelledby={identificador}
    onCancel={(evento) => { evento.preventDefault(); if (!ocupado) cerrar(); }}>
    <div className="ventana-planificador__cabecera"><div><h2 id={identificador}>{titulo}</h2><p>{descripcion}</p></div>
      <button className="boton-icono" type="button" aria-label="Cerrar" disabled={ocupado} onClick={cerrar}><X size={20} /></button></div>
    {children}
  </dialog>;
}
