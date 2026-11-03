import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { KeyRound, UserRound } from 'lucide-react';
import EncabezadoPagina from '../../components/EncabezadoPagina';
import { cambiarContrasena, guardarPerfil } from '../../services/configuracion';
import useGuardarCuenta from '../../hooks/useGuardarCuenta';
import { CampoCuenta, EstadoGuardado } from './ElementosCuenta';

function FormularioPerfil({ cuenta, actualizar, recargar }) {
  const [nombre, cambiarNombre] = useState(cuenta.nombre);
  const estado = useGuardarCuenta(actualizar);
  return <form className="panel cuenta-tarjeta" onSubmit={(evento) => {
    evento.preventDefault();
    estado.guardar((senal) => guardarPerfil({ nombre: nombre.trim() }, { revision: cuenta.revision, senal }));
  }}>
    <div className="cuenta-tarjeta__titulo"><UserRound size={21} /><div><h2>Información personal</h2>
      <p>Tu nombre aparecerá en el menú de usuario.</p></div></div>
    <fieldset disabled={estado.guardando || estado.desactualizado} className="cuenta-campos">
      <CampoCuenta etiqueta="Nombre visible" nombre="nombre" value={nombre} maxLength={120} required
        autoComplete="name" onChange={(evento) => cambiarNombre(evento.target.value)} error={estado.errores.nombre} />
      <CampoCuenta etiqueta="Correo de acceso" nombre="correo" value={cuenta.correo} type="email" readOnly
        ayuda="Este correo identifica tu cuenta de acceso." />
      <div className="cuenta-datos"><span>Rol: <strong>{cuenta.esAdministrador ? 'Administrador' : 'Usuario'}</strong></span>
        <span>Cuenta creada: <strong>{new Intl.DateTimeFormat('es-PE', { dateStyle: 'long' }).format(new Date(cuenta.creada))}</strong></span></div>
      <button className="boton boton--primario" type="submit">{estado.guardando ? 'Guardando…' : 'Guardar perfil'}</button>
    </fieldset><EstadoGuardado estado={estado} recargar={recargar} />
  </form>;
}

function FormularioContrasena({ cuenta, actualizar, recargar }) {
  const [claves, cambiarClaves] = useState({ contrasenaActual: '', contrasenaNueva: '', confirmar: '' });
  const [errorConfirmacion, cambiarErrorConfirmacion] = useState('');
  const estado = useGuardarCuenta(actualizar);
  const campo = (nombre) => ({ value: claves[nombre], onChange: (evento) => {
    const valor = evento.target.value;
    cambiarErrorConfirmacion(''); cambiarClaves((anteriores) => ({ ...anteriores, [nombre]: valor }));
  } });
  return <form className="panel cuenta-tarjeta" onSubmit={async (evento) => {
    evento.preventDefault();
    if (claves.contrasenaNueva !== claves.confirmar) { cambiarErrorConfirmacion('Las contraseñas nuevas deben coincidir.'); return; }
    const guardado = await estado.guardar((senal) => cambiarContrasena({ contrasenaActual: claves.contrasenaActual,
      contrasenaNueva: claves.contrasenaNueva }, { revision: cuenta.revision, senal }));
    if (guardado) cambiarClaves({ contrasenaActual: '', contrasenaNueva: '', confirmar: '' });
  }}>
    <div className="cuenta-tarjeta__titulo"><KeyRound size={21} /><div><h2>Cambiar contraseña</h2>
      <p>Confirma tu contraseña actual para guardar una nueva.</p></div></div>
    <fieldset className="cuenta-campos" disabled={estado.guardando || estado.desactualizado}>
      <CampoCuenta etiqueta="Contraseña actual" nombre="contrasenaActual" type="password" autoComplete="current-password"
        required maxLength={1024} {...campo('contrasenaActual')} error={estado.errores.contrasenaActual} />
      <CampoCuenta etiqueta="Nueva contraseña" nombre="contrasenaNueva" type="password" autoComplete="new-password"
        required minLength={12} maxLength={128} {...campo('contrasenaNueva')} error={estado.errores.contrasenaNueva}
        ayuda="Usa entre 12 y 128 caracteres. Puedes utilizar una frase larga." />
      <CampoCuenta etiqueta="Confirmar nueva contraseña" nombre="confirmar" type="password" autoComplete="new-password"
        required minLength={12} maxLength={128} {...campo('confirmar')} error={errorConfirmacion} />
      <button className="boton boton--primario" type="submit">{estado.guardando ? 'Guardando…' : 'Actualizar contraseña'}</button>
    </fieldset><EstadoGuardado estado={estado} recargar={recargar} />
  </form>;
}

export default function MiPerfil() {
  const { cuenta, actualizarCuenta, recargarConfiguracion } = useOutletContext();
  return <><EncabezadoPagina titulo="Mi perfil" descripcion="Administra tus datos personales y el acceso a tu cuenta." />
    <div className="cuenta-rejilla"><FormularioPerfil cuenta={cuenta} actualizar={actualizarCuenta} recargar={recargarConfiguracion} />
      <FormularioContrasena cuenta={cuenta} actualizar={actualizarCuenta} recargar={recargarConfiguracion} /></div></>;
}
