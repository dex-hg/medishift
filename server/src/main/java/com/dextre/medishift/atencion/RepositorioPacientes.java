package com.dextre.medishift.atencion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dextre.medishift.atencion.DatosAtencion.*;
import com.dextre.medishift.catalogos.CatalogoException;
import com.dextre.medishift.programacion.RevisionesProgramacion;

@Repository
public class RepositorioPacientes {
    private final JdbcTemplate consultas;
    private static final String VINCULADO = """
            EXISTS (SELECT 1 FROM (
                SELECT id_institucion,id_paciente,id_profesional FROM paciente_profesional
                UNION ALL SELECT id_institucion,id_paciente,id_profesional FROM cita
            ) v WHERE v.id_institucion=p.id_institucion AND v.id_paciente=p.id AND v.id_profesional=?)
            """;

    public RepositorioPacientes(JdbcTemplate consultas) { this.consultas = consultas; }

    public void verificarEsquema() {
        if (!Boolean.TRUE.equals(consultas.queryForObject("SELECT to_regclass(current_schema() || '.cita') IS NOT NULL", Boolean.class))) {
            throw CatalogoException.noDisponible("El módulo de pacientes y citas requiere aplicar V006. Contacta al administrador de la instalación.");
        }
    }

    public PaginaPacientes listar(UUID institucion, UUID profesional, String buscar, int pagina) {
        String filtro = " WHERE p.id_institucion=? AND (position(lower(?) in lower(p.nombres || ' ' || p.apellidos || ' ' || p.dni || ' ' || p.codigo)) > 0)";
        if (profesional != null) filtro += " AND " + VINCULADO;
        Object[] parametros = profesional == null ? new Object[]{institucion, buscar} : new Object[]{institucion, buscar, profesional};
        long total = consultas.queryForObject("SELECT count(*) FROM paciente p" + filtro, Long.class, parametros);
        var argumentos = new java.util.ArrayList<Object>(List.of(parametros));
        argumentos.add(pagina * 30);
        List<ResumenPaciente> pacientes = consultas.query("SELECT p.* FROM paciente p" + filtro + " ORDER BY lower(p.apellidos),lower(p.nombres),p.id LIMIT 30 OFFSET ?",
                (fila, numero) -> resumen(fila, true), argumentos.toArray());
        return new PaginaPacientes(pacientes, total, pagina);
    }

    public List<ResumenPaciente> buscarDni(UUID institucion, UUID profesional, String dni) {
        return consultas.query("SELECT p.*, (?::uuid IS NULL OR " + VINCULADO + ") AS puede_consultar FROM paciente p WHERE p.id_institucion=? AND p.dni=?",
                (fila, numero) -> resumen(fila, fila.getBoolean("puede_consultar")), profesional, profesional, institucion, dni);
    }

    public boolean vinculado(UUID institucion, UUID paciente, UUID profesional) {
        return Boolean.TRUE.equals(consultas.queryForObject("SELECT EXISTS (SELECT 1 FROM paciente p WHERE p.id_institucion=? AND p.id=? AND " + VINCULADO + ")",
                Boolean.class, institucion, paciente, profesional));
    }

    public void vincular(UUID institucion, UUID paciente, UUID profesional) {
        consultas.update("INSERT INTO paciente_profesional VALUES (?,?,?) ON CONFLICT DO NOTHING", institucion, paciente, profesional);
    }

    public Paciente buscar(UUID institucion, UUID id) {
        return consultas.query("SELECT * FROM paciente WHERE id_institucion=? AND id=?", (fila, numero) -> leer(fila), institucion, id)
                .stream().findFirst().orElseThrow(CatalogoException::noEncontrado);
    }

    public boolean dniRepetido(UUID institucion, String dni, UUID excluir) {
        return Boolean.TRUE.equals(consultas.queryForObject("SELECT EXISTS (SELECT 1 FROM paciente WHERE id_institucion=? AND dni=? AND id<>?)",
                Boolean.class, institucion, dni, excluir));
    }

    public void insertar(UUID institucion, UUID id, SolicitudPaciente datos) {
        consultas.update("INSERT INTO paciente(id,id_institucion,codigo,nombres,apellidos,dni,fecha_nacimiento,correo,telefono,activo) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, institucion, "PAC-" + id.toString().replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT),
                datos.nombres(), datos.apellidos(), datos.dni(), datos.fechaNacimiento(), datos.correo(), datos.telefono(), datos.activo());
    }

    public void actualizar(UUID institucion, UUID id, SolicitudPaciente datos) {
        consultas.update("UPDATE paciente SET nombres=?,apellidos=?,dni=?,fecha_nacimiento=?,correo=?,telefono=?,activo=?,version=version+1 WHERE id_institucion=? AND id=?",
                datos.nombres(), datos.apellidos(), datos.dni(), datos.fechaNacimiento(), datos.correo(), datos.telefono(), datos.activo(), institucion, id);
    }

    /** La auditoría registra identidades técnicas, sin copiar DNI ni contacto. */
    public void auditar(UUID institucion, UUID actor, UUID id, String accion) {
        consultas.update("""
                INSERT INTO audit_event(id_audit_event,id_institution,id_actor_user_account,entity_name_audit_event,entity_id_audit_event,action_audit_event,after_audit_event)
                SELECT ?,?,?,'paciente',?,?,jsonb_build_object('version',version,'activo',activo)
                FROM paciente WHERE id_institucion=? AND id=?
                """, UUID.randomUUID(), institucion, actor, id, accion, institucion, id);
    }

    private ResumenPaciente resumen(ResultSet fila, boolean puedeConsultar) throws SQLException {
        return new ResumenPaciente(fila.getObject("id", UUID.class), fila.getString("codigo"), fila.getString("nombres"),
                fila.getString("apellidos"), fila.getString("dni"), fila.getBoolean("activo"), puedeConsultar);
    }

    private Paciente leer(ResultSet fila) throws SQLException {
        UUID id = fila.getObject("id", UUID.class);
        return new Paciente(id, fila.getString("codigo"), fila.getString("nombres"), fila.getString("apellidos"), fila.getString("dni"),
                fila.getObject("fecha_nacimiento", LocalDate.class), fila.getString("correo"), fila.getString("telefono"), fila.getBoolean("activo"),
                RevisionesProgramacion.resumir(id, fila.getLong("version")));
    }
}
