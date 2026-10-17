CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES accounts(id),
    barber_id UUID NOT NULL REFERENCES barber_profiles(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status = 'CONFIRMED'),
    total NUMERIC(19,2) NOT NULL CHECK (total > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT appointment_one_hour CHECK (ends_at = starts_at + INTERVAL '1 hour'),
    CONSTRAINT appointment_exact_hour CHECK (
        date_trunc('hour', starts_at AT TIME ZONE 'America/Mexico_City')
        = starts_at AT TIME ZONE 'America/Mexico_City')
);
-- Fixed one-hour slots aligned to local hours cannot partially overlap.
CREATE UNIQUE INDEX appointments_confirmed_slot ON appointments(barber_id, starts_at)
    WHERE status = 'CONFIRMED';
CREATE INDEX appointments_client_start ON appointments(client_id, starts_at, id);
CREATE TABLE appointment_services (
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    service_id UUID NOT NULL REFERENCES catalog_services(id),
    name VARCHAR(120) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    type VARCHAR(7) NOT NULL CHECK (type IN ('HAIRCUT', 'ADD_ON')),
    PRIMARY KEY (appointment_id, position),
    UNIQUE (appointment_id, service_id),
    CHECK ((position = 0 AND type = 'HAIRCUT') OR (position > 0 AND type = 'ADD_ON'))
);
