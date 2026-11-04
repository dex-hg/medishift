-- La programación sigue siendo administrativa hasta que se habilite explícitamente.
ALTER TABLE institution_settings
    ADD COLUMN IF NOT EXISTS medical_self_scheduling boolean NOT NULL DEFAULT false;
