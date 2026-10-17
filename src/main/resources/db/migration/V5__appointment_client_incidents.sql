ALTER TABLE accounts
    ADD COLUMN appointment_incident_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN commercial_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN appointment_event_sequence BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT account_appointment_incident_count CHECK (appointment_incident_count >= 0),
    ADD CONSTRAINT account_appointment_event_sequence CHECK (appointment_event_sequence >= 0);

-- Sequence is assigned while holding the account row lock. The appointment
-- uniqueness constraint independently prevents a terminal event being counted twice.
CREATE TABLE appointment_client_events (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('CLIENT_CANCELLED','NO_SHOW','COMPLETED','ADMIN_REACTIVATED')),
    appointment_id UUID UNIQUE REFERENCES appointments(id),
    actor_id UUID REFERENCES accounts(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500),
    resulting_count INTEGER NOT NULL CHECK (resulting_count >= 0),
    resulting_blocked BOOLEAN NOT NULL,
    UNIQUE (account_id, sequence),
    CHECK ((kind='ADMIN_REACTIVATED' AND appointment_id IS NULL AND actor_id IS NOT NULL
            AND reason IS NOT NULL AND length(btrim(reason))>0)
        OR (kind<>'ADMIN_REACTIVATED' AND appointment_id IS NOT NULL AND reason IS NULL))
);
CREATE INDEX appointment_client_events_account ON appointment_client_events(account_id, sequence);

-- Existing V4 rows have no completed_by/no_show_by field. Preserve their
-- historical terminal events; actor_id is unknown only for those legacy rows.
-- Order by the actual transition timestamp, then appointment UUID for ties.
DO $$
DECLARE
    event_row RECORD;
    incident_count INTEGER;
    blocked BOOLEAN;
    event_sequence BIGINT;
BEGIN
    FOR event_row IN
        SELECT id, client_id, cancelled_by AS actor_id, cancelled_at AS event_at,
               'CLIENT_CANCELLED'::VARCHAR AS kind
        FROM appointments WHERE client_id IS NOT NULL AND status='CANCELLED' AND cancellation_origin='CLIENT'
        UNION ALL
        SELECT id, client_id, NULL::UUID, no_show_at, 'NO_SHOW'
        FROM appointments WHERE client_id IS NOT NULL AND status='NO_SHOW'
        UNION ALL
        SELECT id, client_id, NULL::UUID, completed_at, 'COMPLETED'
        FROM appointments WHERE client_id IS NOT NULL AND status='COMPLETED'
        ORDER BY client_id, event_at, id
    LOOP
        SELECT appointment_incident_count, commercial_blocked, appointment_event_sequence
        INTO incident_count, blocked, event_sequence FROM accounts WHERE id=event_row.client_id;
        event_sequence := event_sequence + 1;
        IF event_row.kind='COMPLETED' THEN incident_count := 0;
        ELSE incident_count := incident_count + 1; END IF;
        IF incident_count >= 3 THEN blocked := TRUE; END IF;
        UPDATE accounts SET appointment_incident_count=incident_count, commercial_blocked=blocked,
            appointment_event_sequence=event_sequence WHERE id=event_row.client_id;
        INSERT INTO appointment_client_events(id, account_id, sequence, kind, appointment_id,
            actor_id, occurred_at, resulting_count, resulting_blocked)
        VALUES (event_row.id, event_row.client_id, event_sequence, event_row.kind, event_row.id,
            event_row.actor_id, event_row.event_at, incident_count, blocked);
    END LOOP;
END;
$$;
