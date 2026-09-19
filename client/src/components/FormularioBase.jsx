import { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';

function FormularioBase({ children, accionesSecundarias }) {
  const [mensaje, setMensaje] = useState('');

  const validarFormulario = (evento) => {
    evento.preventDefault();
    const formulario = evento.currentTarget;
    const horaInicio = formulario.elements.namedItem('horaInicio');
    const horaFin = formulario.elements.namedItem('horaFin');
    const fechaInicio = formulario.elements.namedItem('fechaInicio');
    const fechaFin = formulario.elements.namedItem('fechaFin');

    if (horaFin instanceof HTMLInputElement) {
      horaFin.setCustomValidity('');
      if (horaInicio?.value && horaFin.value && horaFin.value <= horaInicio.value) {
        horaFin.setCustomValidity('La hora de fin debe ser posterior a la hora de inicio.');
      }
    }

    if (fechaFin instanceof HTMLInputElement) {
      fechaFin.setCustomValidity('');
      if (fechaInicio?.value && fechaFin.value && fechaFin.value < fechaInicio.value) {
        fechaFin.setCustomValidity('La fecha de fin no puede ser anterior a la fecha de inicio.');
      }
    }

    if (!formulario.checkValidity()) {
      setMensaje('Revisa los campos obligatorios antes de continuar.');
      formulario.reportValidity();
      return;
    }

    setMensaje('Los datos ingresados son válidos.');
  };

  return (
    <form className="formulario" onSubmit={validarFormulario} noValidate>
      {children}

      {mensaje && (
        <div className="aviso aviso--resultado" role="status">
          <CheckCircle2 size={18} aria-hidden="true" />
          <p>{mensaje}</p>
        </div>
      )}

      <div className="formulario__acciones">
        {accionesSecundarias}
        <button className="boton boton--primario" type="submit">
          Guardar
        </button>
      </div>
    </form>
  );
}

export default FormularioBase;
