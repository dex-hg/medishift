-- Datos administrativos institucionales. No crea cuentas ni historia clínica.
CREATE TABLE paciente (
    id uuid PRIMARY KEY,
    id_institucion uuid NOT NULL REFERENCES institution(id_institution),
    codigo varchar(20) NOT NULL,
    nombres varchar(80) NOT NULL CHECK (btrim(nombres) <> ''),
    apellidos varchar(100) NOT NULL CHECK (btrim(apellidos) <> ''),
    dni varchar(8) NOT NULL CHECK (dni ~ '^[0-9]{8}$'),
    fecha_nacimiento date NOT NULL CHECK (isfinite(fecha_nacimiento) AND fecha_nacimiento BETWEEN '0001-01-01' AND '9999-12-31'),
    correo varchar(254) NOT NULL DEFAULT '',
    telefono varchar(20) NOT NULL DEFAULT '',
    activo boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    creado_en timestamptz NOT NULL DEFAULT now(),
    UNIQUE(id_institucion,id),
    UNIQUE(id_institucion,codigo),
    UNIQUE(id_institucion,dni)
);

CREATE TABLE paciente_profesional (
    id_institucion uuid NOT NULL,
    id_paciente uuid NOT NULL,
    id_profesional uuid NOT NULL,
    PRIMARY KEY(id_institucion,id_paciente,id_profesional),
    FOREIGN KEY(id_institucion,id_paciente) REFERENCES paciente(id_institucion,id),
    FOREIGN KEY(id_institucion,id_profesional) REFERENCES professional(id_institution,id_professional)
);
CREATE INDEX paciente_profesional_medico ON paciente_profesional(id_institucion,id_profesional,id_paciente);

CREATE TABLE cita (
    id uuid PRIMARY KEY,
    id_institucion uuid NOT NULL REFERENCES institution(id_institution),
    id_paciente uuid NOT NULL,
    id_turno uuid NOT NULL,
    id_profesional uuid NOT NULL,
    id_consultorio uuid NOT NULL,
    inicio timestamptz NOT NULL,
    fin timestamptz NOT NULL,
    estado varchar(16) NOT NULL DEFAULT 'programada',
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    id_creador uuid NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(id_institucion,id_paciente) REFERENCES paciente(id_institucion,id),
    FOREIGN KEY(id_institucion,id_turno) REFERENCES shift(id_institution,id_shift),
    FOREIGN KEY(id_institucion,id_profesional) REFERENCES professional(id_institution,id_professional),
    FOREIGN KEY(id_institucion,id_consultorio) REFERENCES room(id_institution,id_room),
    FOREIGN KEY(id_institucion,id_creador) REFERENCES user_account(id_institution,id_user_account),
    CHECK (isfinite(inicio) AND isfinite(fin) AND fin > inicio),
    CHECK (estado IN ('programada','atendida','cancelada','no_asistio')),
    EXCLUDE USING gist (id_institucion WITH =,id_profesional WITH =,tstzrange(inicio,fin,'[)') WITH &&)
        WHERE (estado <> 'cancelada'),
    EXCLUDE USING gist (id_institucion WITH =,id_paciente WITH =,tstzrange(inicio,fin,'[)') WITH &&)
        WHERE (estado <> 'cancelada')
);
CREATE INDEX cita_agenda ON cita(id_institucion,inicio,id);
CREATE INDEX cita_paciente ON cita(id_institucion,id_paciente,inicio DESC,id);
CREATE INDEX cita_turno ON cita(id_institucion,id_turno);
CREATE INDEX cita_consultorio ON cita(id_institucion,id_consultorio);

CREATE TABLE historial_plan_mensual (
    id uuid PRIMARY KEY,
    id_institucion uuid NOT NULL,
    id_plan uuid NOT NULL,
    version integer NOT NULL,
    contenido jsonb NOT NULL,
    id_actor uuid NOT NULL,
    archivado_en timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(id_institucion,id_plan) REFERENCES schedule(id_institution,id_schedule),
    FOREIGN KEY(id_institucion,id_actor) REFERENCES user_account(id_institution,id_user_account),
    UNIQUE(id_institucion,id_plan,version)
);

-- Defensa adicional a los bloqueos y validaciones de los servicios.
CREATE FUNCTION validar_vinculos_cita() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE turno shift%ROWTYPE; estado_plan text;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.estado <> 'programada' THEN
            RAISE EXCEPTION 'La cita ya tiene un estado final' USING ERRCODE='23514';
        END IF;
        IF NEW.id_paciente <> OLD.id_paciente OR NEW.id_institucion <> OLD.id_institucion THEN
            RAISE EXCEPTION 'La identidad de la cita es inmutable' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.estado <> 'programada' THEN
        RAISE EXCEPTION 'Las citas nuevas deben estar programadas' USING ERRCODE='23514';
    END IF;
    IF NEW.estado = 'programada' THEN
        SELECT * INTO turno FROM shift WHERE id_institution=NEW.id_institucion AND id_shift=NEW.id_turno FOR SHARE;
        SELECT status_schedule INTO estado_plan FROM schedule
            WHERE id_institution=NEW.id_institucion AND id_schedule=turno.id_schedule FOR SHARE;
        IF turno.id_shift IS NULL OR turno.status_shift <> 'approved' OR estado_plan <> 'approved'
            OR turno.id_professional <> NEW.id_profesional OR turno.id_room <> NEW.id_consultorio
            OR NEW.inicio < turno.start_at_shift OR NEW.fin > turno.end_at_shift OR NEW.inicio <= now()
            OR NOT EXISTS (SELECT 1 FROM paciente WHERE id_institucion=NEW.id_institucion AND id=NEW.id_paciente AND activo)
        THEN RAISE EXCEPTION 'La cita requiere paciente activo y turno aprobado compatible' USING ERRCODE='23514'; END IF;
    ELSIF NEW.estado = 'atendida' AND NEW.inicio > now() THEN
        RAISE EXCEPTION 'La cita todavía no comienza' USING ERRCODE='23514';
    ELSIF NEW.estado = 'no_asistio' AND NEW.fin > now() THEN
        RAISE EXCEPTION 'La cita todavía no termina' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER validar_cita BEFORE INSERT OR UPDATE ON cita FOR EACH ROW EXECUTE FUNCTION validar_vinculos_cita();

CREATE FUNCTION proteger_turno_con_citas() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (SELECT 1 FROM cita WHERE id_institucion=OLD.id_institution AND id_turno=OLD.id_shift) THEN
            RAISE EXCEPTION 'El turno tiene historial de citas' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF EXISTS (SELECT 1 FROM cita c WHERE c.id_institucion=OLD.id_institution AND c.id_turno=OLD.id_shift
        AND c.estado <> 'cancelada' AND (NEW.id_professional <> c.id_profesional OR NEW.id_room <> c.id_consultorio
        OR NEW.start_at_shift > c.inicio OR NEW.end_at_shift < c.fin OR NEW.status_shift IN ('cancelled','superseded'))) THEN
        RAISE EXCEPTION 'Resuelve las citas afectadas antes de cambiar el turno' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER proteger_citas_turno BEFORE UPDATE OR DELETE ON shift FOR EACH ROW EXECUTE FUNCTION proteger_turno_con_citas();

CREATE FUNCTION proteger_plan_con_citas() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status_schedule IN ('cancelled','superseded') AND EXISTS (
        SELECT 1 FROM cita c JOIN shift t ON t.id_institution=c.id_institucion AND t.id_shift=c.id_turno
        WHERE t.id_institution=OLD.id_institution AND t.id_schedule=OLD.id_schedule AND c.estado <> 'cancelada') THEN
        RAISE EXCEPTION 'El horario conserva citas vinculadas' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER proteger_citas_plan BEFORE UPDATE ON schedule FOR EACH ROW EXECUTE FUNCTION proteger_plan_con_citas();
