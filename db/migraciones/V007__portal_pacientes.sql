-- Identidad del portal por DNI. Las fichas siguen perteneciendo a cada institución.
CREATE TABLE cuenta_portal_paciente (
    id uuid PRIMARY KEY,
    dni varchar(8) NOT NULL UNIQUE CHECK (dni ~ '^[0-9]{8}$'),
    hash_contrasena text NOT NULL CHECK (btrim(hash_contrasena) <> ''),
    creada_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE codigo_activacion_paciente (
    dni varchar(8) PRIMARY KEY CHECK (dni ~ '^[0-9]{8}$'),
    id_institucion uuid NOT NULL,
    id_paciente uuid NOT NULL,
    hash_codigo varchar(64) NOT NULL CHECK (hash_codigo ~ '^[0-9a-f]{64}$'),
    vence_en timestamptz NOT NULL,
    CONSTRAINT codigo_activacion_ficha FOREIGN KEY (id_institucion, id_paciente)
        REFERENCES paciente(id_institucion, id) ON DELETE CASCADE
);

CREATE INDEX codigo_activacion_paciente_ficha ON codigo_activacion_paciente(id_institucion, id_paciente);
CREATE INDEX paciente_dni_activo_portal ON paciente(dni, id_institucion) WHERE activo;
