import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Building2, LogIn, ShieldCheck, Sparkles } from 'lucide-react';
import CampoRegistro from '../components/CampoRegistro';
import { iniciarSesion } from '../services/sesion';
import {
  LIMITES_INICIO_SESION, normalizarDatosInicioSesion, validarInicioSesion,
} from '../utils/validacionesInicioSesion';

function obtenerDatosIniciales(estado) {
  const datosPublicos = estado && typeof estado === 'object' && !Array.isArray(estado) ? estado : {};
  return normalizarDatosInicioSesion({
    codigoInstitucion: typeof datosPublicos.codigoInstitucion === 'string' ? datosPublicos.codigoInstitucion : '',
    correo: typeof datosPublicos.correo === 'string' ? datosPublicos.correo : '',
    contrasena: '',
  });
}

function InicioSesion() {
  const ubicacion = useLocation();
  const navegar = useNavigate();
  const identificador = useId();
  const formulario = useRef(null);
  const tituloFormulario = useRef(null);
  const avisoAcceso = useRef(null);
  const campoConError = useRef(null);
  const solicitudEnCurso = useRef(false);
  const [datos, setDatos] = useState(() => obtenerDatosIniciales(ubicacion.state));
  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    const tituloAnterior = document.title;
    document.title = 'Iniciar sesión | MediShift';
    tituloFormulario.current?.focus();
    return () => { document.title = tituloAnterior; };
  }, []);

  useEffect(() => {
    if (enviando) return;
    if (campoConError.current) {
      Array.from(formulario.current?.elements || [])
        .find((campo) => campo.name === campoConError.current)?.focus();
      campoConError.current = null;
    } else if (mensaje) avisoAcceso.current?.focus();
  }, [errores, mensaje, enviando]);

  const actualizarCampo = (evento) => {
    if (solicitudEnCurso.current) return;
    const { name: nombre, value: valor } = evento.target;
    setDatos((anteriores) => ({ ...anteriores, [nombre]: valor }));
    setMensaje('');
    setErrores((anteriores) => {
      const siguientes = { ...anteriores };
      delete siguientes[nombre];
      return siguientes;
    });
  };

  const revisarAcceso = async (evento) => {
    evento.preventDefault();
    if (solicitudEnCurso.current) return;
    const datosNormalizados = normalizarDatosInicioSesion(datos);
    const erroresAcceso = validarInicioSesion(datosNormalizados);
    setDatos(datosNormalizados);
    setErrores(erroresAcceso);
    campoConError.current = Array.from(evento.currentTarget.elements)
      .find((campo) => erroresAcceso[campo.name])?.name || null;
    setMensaje('');
    if (Object.keys(erroresAcceso).length) return;

    solicitudEnCurso.current = true;
    setEnviando(true);
    let accesoConfirmado = false;
    try {
      await iniciarSesion(datosNormalizados);
      accesoConfirmado = true;
      setDatos((anteriores) => ({ ...anteriores, contrasena: '' }));
      const destino = ubicacion.state?.destino;
      const rutaInterna = typeof destino === 'string' && /^\/(?!\/)[^\\\r\n]*$/.test(destino)
        && !destino.startsWith('/iniciar-sesion');
      navegar(rutaInterna ? destino : '/panel', { replace: true, state: null });
    } catch (error) {
      const erroresRespuesta = error.errores || {};
      campoConError.current = ['codigoInstitucion', 'correo', 'contrasena']
        .find((campo) => erroresRespuesta[campo]) || null;
      setErrores(erroresRespuesta);
      setMensaje(error.message || 'No se pudo iniciar sesión. Inténtalo nuevamente.');
    } finally {
      solicitudEnCurso.current = accesoConfirmado;
      setEnviando(false);
    }
  };

  const evitarSalidaEnCurso = (evento) => {
    if (solicitudEnCurso.current) evento.preventDefault();
  };

  const campos = [
    {
      nombre: 'codigoInstitucion', etiqueta: 'Código de institución', autoComplete: 'off',
      ayuda: 'El código con el que se registró tu institución.', placeholder: 'Por ejemplo: CLINICA-SUR',
    },
    {
      nombre: 'correo', etiqueta: 'Correo de tu cuenta', tipo: 'email', autoComplete: 'username',
      ayuda: 'El correo vinculado a esta institución.', placeholder: 'nombre@institucion.pe',
      maxLength: LIMITES_INICIO_SESION.correo,
    },
    {
      nombre: 'contrasena', etiqueta: 'Contraseña', tipo: 'password', autoComplete: 'current-password',
      ayuda: 'Usa la contraseña de tu cuenta.',
    },
  ];

  return (
    <div className="registro inicio-sesion">
      <header className="registro__encabezado">
        <Link className="portada-marca" to="/" aria-label="MediShift, página principal"
          onClick={evitarSalidaEnCurso} aria-disabled={enviando || undefined} tabIndex={enviando ? -1 : undefined}>
          <span className="portada-marca__simbolo" aria-hidden="true"><Sparkles size={20} /></span>
          <span><strong>MediShift</strong><small>Gestión operativa</small></span>
        </Link>
        <Link className="registro__volver" to="/" onClick={evitarSalidaEnCurso}
          aria-disabled={enviando || undefined} tabIndex={enviando ? -1 : undefined}>
          <ArrowLeft size={16} aria-hidden="true" /> Volver al inicio
        </Link>
      </header>

      <main className="registro__contenido inicio-sesion__contenido">
        <section className="registro-presentacion inicio-sesion__presentacion" aria-labelledby={`${identificador}-titulo`}>
          <span className="registro-presentacion__etiqueta">
            <LogIn size={16} aria-hidden="true" /> Acceso a MediShift
          </span>
          <h1 id={`${identificador}-titulo`}>Tu institución, sus recursos y cada turno.</h1>
          <p>Un espacio para organizar profesionales, consultorios y horarios de atención.</p>
          <ul className="registro-presentacion__beneficios">
            <li><Building2 size={20} aria-hidden="true" /><div>
              <strong>El código identifica tu institución</strong>
              <span>Cada cuenta pertenece a una clínica u hospital.</span>
            </div></li>
            <li><ShieldCheck size={20} aria-hidden="true" /><div>
              <strong>Una cuenta personal</strong>
              <span>Usa tu correo y conserva tu contraseña en privado.</span>
            </div></li>
          </ul>
        </section>

        <section className="registro-tarjeta inicio-sesion__tarjeta" aria-labelledby={`${identificador}-formulario`}>
          <form ref={formulario} className="registro-formulario" onSubmit={revisarAcceso} noValidate aria-busy={enviando}>
            <div className="registro-formulario__titulo">
              <span className="registro-formulario__icono" aria-hidden="true"><LogIn size={22} /></span>
              <div>
                <h2 ref={tituloFormulario} tabIndex={-1} id={`${identificador}-formulario`}>Iniciar sesión</h2>
                <p>Ingresa los datos de tu cuenta.</p>
              </div>
            </div>
            <p className="registro-formulario__obligatorios">Todos los campos son obligatorios.</p>
            <div className="registro-formulario__campos">
              {campos.map((campo) => (
                <CampoRegistro key={campo.nombre} {...campo}
                  identificador={`${identificador}-${campo.nombre}`} value={datos[campo.nombre]}
                  error={errores[campo.nombre]} onChange={actualizarCampo} disabled={enviando} />
              ))}
            </div>
            {mensaje && (
              <div className="registro-resultado registro-resultado--error" ref={avisoAcceso} tabIndex={-1} role="alert">
                <p>{mensaje}</p>
              </div>
            )}
            <div className="registro-formulario__acciones">
              <button className="boton boton--primario" type="submit" disabled={enviando}>
                {enviando ? 'Ingresando…' : 'Iniciar sesión'} <ArrowRight size={18} aria-hidden="true" />
              </button>
            </div>
            <p className="registro-formulario__alcance" role={enviando ? 'status' : undefined}>
              {enviando ? 'Estamos verificando tus datos.' : 'Accede con los datos vinculados a tu institución.'}
            </p>
            <p className="inicio-sesion__registro">¿Tu institución aún no está registrada?{' '}
              <Link className="inicio-sesion__enlace" to="/registro" onClick={evitarSalidaEnCurso}
                aria-disabled={enviando || undefined} tabIndex={enviando ? -1 : undefined}>
                Registrar institución
              </Link>
            </p>
          </form>
        </section>
      </main>
      <footer className="registro__pie">MediShift · Gestión de profesionales, consultorios y horarios.</footer>
    </div>
  );
}

export default InicioSesion;
