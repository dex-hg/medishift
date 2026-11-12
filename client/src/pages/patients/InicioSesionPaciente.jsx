import { useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, CalendarDays, Sparkles } from 'lucide-react';
import CampoRegistro from '../../components/CampoRegistro';
import { iniciarSesionPaciente } from '../../services/portalPaciente';
import { fechaActual, fechaValida } from '../../utils/validacionesProgramacion';

const FORMULARIO_VACIO = { dni: '', fechaNacimiento: '' };

export default function InicioSesionPaciente() {
  const identificador = useId();
  const navegar = useNavigate();
  const [datos, cambiarDatos] = useState(FORMULARIO_VACIO);
  const [errores, cambiarErrores] = useState({});
  const [mensaje, cambiarMensaje] = useState('');
  const [enviando, cambiarEnviando] = useState(false);
  const bloqueo = useRef(false);
  const hoy = fechaActual();

  const actualizar = (evento) => {
    const { name: campo, value: valor } = evento.target;
    cambiarDatos({ ...datos, [campo]: valor });
    cambiarErrores({ ...errores, [campo]: '' });
    cambiarMensaje('');
  };

  const mostrarErrores = (fallos) => {
    cambiarErrores(fallos);
    const campo = Object.keys(fallos)[0];
    if (campo) requestAnimationFrame(() => document.getElementById(identificador + '-' + campo)?.focus());
  };

  const enviar = async (evento) => {
    evento.preventDefault();
    if (bloqueo.current) return;
    const dni = datos.dni.trim();
    const { fechaNacimiento } = datos;
    const fallos = {};
    if (!/^[0-9]{8}$/.test(dni)) fallos.dni = 'Ingresa un DNI de 8 dígitos.';
    if (!fechaValida(fechaNacimiento) || fechaNacimiento > hoy) {
      fallos.fechaNacimiento = 'Ingresa una fecha de nacimiento válida.';
    }
    mostrarErrores(fallos);
    cambiarMensaje('');
    if (Object.keys(fallos).length) return;
    bloqueo.current = true;
    cambiarEnviando(true);
    try {
      await iniciarSesionPaciente({ dni, fechaNacimiento });
      navegar('/mis-citas', { replace: true });
    } catch (fallo) {
      mostrarErrores(fallo.errores || {});
      cambiarMensaje(fallo.message);
    } finally {
      bloqueo.current = false;
      cambiarEnviando(false);
    }
  };

  const campos = [
    { nombre: 'dni', etiqueta: 'DNI', autoComplete: 'off', inputMode: 'numeric', maxLength: 8 },
    { nombre: 'fechaNacimiento', etiqueta: 'Fecha de nacimiento', tipo: 'date', autoComplete: 'off', min: '0001-01-01', max: hoy },
  ];

  return <div className="registro inicio-sesion">
    <header className="registro__encabezado">
      <Link className="portada-marca" to="/" aria-label="MediShift, página principal">
        <span className="portada-marca__simbolo"><Sparkles size={20} aria-hidden="true" /></span>
        <strong>MediShift</strong>
      </Link>
      <Link className="registro__volver" to="/iniciar-sesion">
        <ArrowLeft size={16} aria-hidden="true" /> Acceso del personal
      </Link>
    </header>
    <main className="registro__contenido inicio-sesion__contenido">
      <section className="registro-presentacion inicio-sesion__presentacion">
        <span className="registro-presentacion__etiqueta"><CalendarDays size={16} aria-hidden="true" /> Portal del paciente</span>
        <h1>Tus citas en un solo calendario</h1>
        <p>Consulta tus citas y revisa en qué hospital, consultorio y con quién te atenderás.</p>
      </section>
      <section className="registro-tarjeta inicio-sesion__tarjeta" aria-labelledby={identificador + '-titulo'}>
        <form className="registro-formulario" noValidate onSubmit={enviar}>
          <div className="registro-formulario__titulo">
            <div>
              <h2 id={identificador + '-titulo'}>Consulta tus citas</h2>
              <p>Ingresa el DNI y la fecha de nacimiento que registraste en el hospital.</p>
            </div>
          </div>
          <div className="registro-formulario__campos">
            {campos.map((campo) => <CampoRegistro key={campo.nombre} {...campo}
              identificador={identificador + '-' + campo.nombre} value={datos[campo.nombre]}
              error={errores[campo.nombre]} onChange={actualizar} disabled={enviando} />)}
          </div>
          {mensaje && <div className="registro-resultado registro-resultado--error" role="alert"><p>{mensaje}</p></div>}
          <div className="registro-formulario__acciones">
            <button className="boton boton--primario" type="submit" disabled={enviando}>
              {enviando ? 'Verificando…' : 'Ver mis citas'} <ArrowRight size={18} aria-hidden="true" />
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>;
}
