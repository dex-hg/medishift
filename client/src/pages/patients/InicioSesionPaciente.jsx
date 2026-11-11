import { useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, CalendarDays, Sparkles } from 'lucide-react';
import CampoRegistro from '../../components/CampoRegistro';
import { activarAccesoPaciente, iniciarSesionPaciente } from '../../services/portalPaciente';

const FORMULARIO_VACIO = { dni: '', codigo: '', contrasena: '', confirmacion: '' };

export default function InicioSesionPaciente() {
  const identificador = useId();
  const navegar = useNavigate();
  const [activar, cambiarActivar] = useState(false);
  const [datos, cambiarDatos] = useState(FORMULARIO_VACIO);
  const [errores, cambiarErrores] = useState({});
  const [mensaje, cambiarMensaje] = useState('');
  const [enviando, cambiarEnviando] = useState(false);
  const bloqueo = useRef(false);
  const titulo = useRef(null);

  const cambiarModo = () => {
    cambiarActivar(!activar);
    cambiarDatos({ ...FORMULARIO_VACIO, dni: datos.dni });
    cambiarErrores({});
    cambiarMensaje('');
    titulo.current?.focus();
  };

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
    const codigo = datos.codigo.trim();
    const contrasena = datos.contrasena;
    const fallos = {};
    if (!/^[0-9]{8}$/.test(dni)) fallos.dni = 'Ingresa un DNI de 8 dígitos.';
    if (!contrasena.trim() || contrasena.length > 128) fallos.contrasena = 'Ingresa tu contraseña.';
    if (activar) {
      if (!/^[A-Za-z0-9_-]{22}$/.test(codigo)) fallos.codigo = 'Ingresa el código de 22 caracteres.';
      if ([...contrasena].length < 12) fallos.contrasena = 'Usa al menos 12 caracteres.';
      if (contrasena !== datos.confirmacion) fallos.confirmacion = 'Las contraseñas deben coincidir.';
    }
    mostrarErrores(fallos);
    cambiarMensaje('');
    if (Object.keys(fallos).length) return;
    bloqueo.current = true;
    cambiarEnviando(true);
    try {
      if (activar) await activarAccesoPaciente({ dni, codigo, contrasena });
      else await iniciarSesionPaciente({ dni, contrasena });
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
    { nombre: 'dni', etiqueta: 'DNI', autoComplete: 'username', inputMode: 'numeric', maxLength: 8 },
    ...(activar ? [{ nombre: 'codigo', etiqueta: 'Código de activación', autoComplete: 'one-time-code', maxLength: 22,
      spellCheck: false, autoCapitalize: 'none' }] : []),
    { nombre: 'contrasena', etiqueta: activar ? 'Crea tu contraseña' : 'Contraseña', tipo: 'password',
      ayuda: activar ? 'Usa al menos 12 caracteres.' : undefined, autoComplete: activar ? 'new-password' : 'current-password', maxLength: 128 },
    ...(activar ? [{ nombre: 'confirmacion', etiqueta: 'Confirma tu contraseña', tipo: 'password',
      autoComplete: 'new-password', maxLength: 128 }] : []),
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
              <h2 ref={titulo} tabIndex={-1} id={identificador + '-titulo'}>{activar ? 'Activa tu acceso' : 'Ingresa con tu DNI'}</h2>
              {activar && <p>Pide tu código de un solo uso en el hospital donde verificaron tu DNI.</p>}
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
              {enviando ? 'Verificando…' : activar ? 'Activar e ingresar' : 'Iniciar sesión'} <ArrowRight size={18} aria-hidden="true" />
            </button>
            <button className="boton boton--fantasma" type="button" disabled={enviando} onClick={cambiarModo}>
              {activar ? 'Ya tengo contraseña' : 'Es mi primer ingreso'}
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>;
}
