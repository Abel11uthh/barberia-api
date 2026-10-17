-- btree_gist supplies GiST equality for UUID. Requires CREATE on this database
-- (trusted extension), or installation by its administrator before migration.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointments DROP CONSTRAINT appointment_exact_hour;
DROP INDEX appointments_confirmed_slot;
ALTER TABLE appointments ALTER COLUMN client_id DROP NOT NULL;
ALTER TABLE appointments
    ADD COLUMN origin VARCHAR(16),
    ADD COLUMN created_by UUID REFERENCES accounts(id),
    ADD COLUMN guest_name VARCHAR(120),
    ADD COLUMN guest_phone VARCHAR(32),
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN no_show_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by UUID REFERENCES accounts(id),
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN cancellation_origin VARCHAR(8);
UPDATE appointments SET origin = 'RESERVATION', created_by = client_id;
ALTER TABLE appointments ALTER COLUMN origin SET NOT NULL;
ALTER TABLE appointments ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE appointments
    ADD CONSTRAINT appointment_status CHECK (status IN ('CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    ADD CONSTRAINT appointment_origin CHECK (origin IN ('RESERVATION','WALK_IN')),
    ADD CONSTRAINT appointment_customer CHECK (
        (origin='RESERVATION' AND client_id IS NOT NULL AND guest_name IS NULL AND guest_phone IS NULL)
        OR (origin='WALK_IN' AND client_id IS NULL AND guest_name IS NOT NULL AND length(btrim(guest_name))>0
            AND guest_phone IS NOT NULL AND length(btrim(guest_phone))>0)),
    ADD CONSTRAINT appointment_start_precision CHECK (
        (origin='RESERVATION' AND date_trunc('hour', starts_at AT TIME ZONE 'America/Mexico_City') = starts_at AT TIME ZONE 'America/Mexico_City')
        OR (origin='WALK_IN' AND date_trunc('minute', starts_at AT TIME ZONE 'America/Mexico_City') = starts_at AT TIME ZONE 'America/Mexico_City')),
    ADD CONSTRAINT appointment_cancellation CHECK (
        (status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND cancellation_origin IS NOT NULL AND cancellation_origin IN ('CLIENT','STAFF')
            AND (cancellation_origin <> 'STAFF' OR (cancellation_reason IS NOT NULL AND length(btrim(cancellation_reason))>0)))
        OR (status<>'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL
            AND cancellation_reason IS NULL AND cancellation_origin IS NULL)),
    ADD CONSTRAINT appointment_progress CHECK (
        (status IN ('CONFIRMED','CANCELLED','NO_SHOW') AND started_at IS NULL AND completed_at IS NULL)
        OR (status='IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status='COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at>=started_at)),
    ADD CONSTRAINT appointment_no_show CHECK (
        (status='NO_SHOW' AND no_show_at IS NOT NULL AND no_show_at>=ends_at)
        OR (status<>'NO_SHOW' AND no_show_at IS NULL));
CREATE INDEX appointments_agenda ON appointments(barber_id, starts_at, id);
CREATE INDEX appointments_branch_agenda ON appointments(branch_id, starts_at, id);

CREATE TABLE barber_blocks (
    id UUID PRIMARY KEY,
    barber_id UUID NOT NULL REFERENCES barber_profiles(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason))>0),
    created_by UUID NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
    cancelled_by UUID REFERENCES accounts(id),
    cancelled_at TIMESTAMPTZ,
    CHECK (starts_at < ends_at),
    CHECK ((status='ACTIVE' AND cancelled_by IS NULL AND cancelled_at IS NULL)
        OR (status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL))
);
CREATE INDEX barber_blocks_agenda ON barber_blocks(barber_id, starts_at, id);

CREATE TABLE agenda_occupations (
    id UUID PRIMARY KEY,
    appointment_id UUID UNIQUE REFERENCES appointments(id),
    block_id UUID UNIQUE REFERENCES barber_blocks(id),
    barber_id UUID NOT NULL REFERENCES barber_profiles(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL,
    CHECK (num_nonnulls(appointment_id, block_id)=1),
    CHECK (starts_at < ends_at),
    CONSTRAINT agenda_no_overlap EXCLUDE USING gist (
        barber_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (active)
);
-- Fail rather than hide inconsistent historical appointments.
INSERT INTO agenda_occupations(id, appointment_id, barber_id, starts_at, ends_at, active)
SELECT id, id, barber_id, starts_at, ends_at, status<>'CANCELLED' FROM appointments;

-- Triggers keep both records atomic even for direct SQL; no separate application
-- write can forget to claim/release the shared interval.
CREATE FUNCTION sync_appointment_occupation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO agenda_occupations(id, appointment_id, barber_id, starts_at, ends_at, active)
    VALUES (NEW.id, NEW.id, NEW.barber_id, NEW.starts_at, NEW.ends_at, NEW.status<>'CANCELLED')
    ON CONFLICT (appointment_id) DO UPDATE SET
        barber_id=EXCLUDED.barber_id, starts_at=EXCLUDED.starts_at,
        ends_at=EXCLUDED.ends_at, active=EXCLUDED.active;
    RETURN NEW;
END;
$$;
CREATE TRIGGER appointment_occupation
AFTER INSERT OR UPDATE OF barber_id, starts_at, ends_at, status ON appointments
FOR EACH ROW EXECUTE FUNCTION sync_appointment_occupation();

CREATE FUNCTION sync_block_occupation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO agenda_occupations(id, block_id, barber_id, starts_at, ends_at, active)
    VALUES (NEW.id, NEW.id, NEW.barber_id, NEW.starts_at, NEW.ends_at, NEW.status='ACTIVE')
    ON CONFLICT (block_id) DO UPDATE SET
        barber_id=EXCLUDED.barber_id, starts_at=EXCLUDED.starts_at,
        ends_at=EXCLUDED.ends_at, active=EXCLUDED.active;
    RETURN NEW;
END;
$$;
CREATE TRIGGER block_occupation
AFTER INSERT OR UPDATE OF barber_id, starts_at, ends_at, status ON barber_blocks
FOR EACH ROW EXECUTE FUNCTION sync_block_occupation();
