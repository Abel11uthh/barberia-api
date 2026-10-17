CREATE TABLE branches (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    address VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE branch_hours (
    branch_id UUID NOT NULL REFERENCES branches(id),
    day VARCHAR(9) NOT NULL CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    closed BOOLEAN NOT NULL,
    opens_at TIME,
    closes_at TIME,
    break_start TIME,
    break_end TIME,
    PRIMARY KEY (branch_id, day),
    CONSTRAINT valid_business_hours CHECK (
        (closed AND opens_at IS NULL AND closes_at IS NULL AND break_start IS NULL AND break_end IS NULL)
        OR
        (NOT closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at < closes_at
            AND ((break_start IS NULL AND break_end IS NULL)
                OR (break_start IS NOT NULL AND break_end IS NOT NULL
                    AND opens_at <= break_start AND break_start < break_end AND break_end <= closes_at)))
    )
);
CREATE TABLE catalog_services (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    type VARCHAR(7) NOT NULL CHECK (type IN ('HAIRCUT','ADD_ON')),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE barber_profiles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX barber_profiles_branch_idx ON barber_profiles(branch_id);

INSERT INTO branches (id, name) VALUES
('10000000-0000-0000-0000-000000000001', 'Sucursal principal'),
('10000000-0000-0000-0000-000000000002', 'Sucursal 2');

INSERT INTO branch_hours (branch_id, day, closed, opens_at, closes_at, break_start, break_end)
SELECT b.id, d.day, d.day = 'SUNDAY',
    CASE WHEN d.day <> 'SUNDAY' THEN TIME '08:00' END,
    CASE WHEN d.day <> 'SUNDAY' THEN TIME '18:00' END,
    CASE WHEN d.day = 'SUNDAY' THEN NULL
         WHEN b.id = '10000000-0000-0000-0000-000000000001' THEN TIME '10:00' ELSE TIME '11:00' END,
    CASE WHEN d.day = 'SUNDAY' THEN NULL
         WHEN b.id = '10000000-0000-0000-0000-000000000001' THEN TIME '11:00' ELSE TIME '12:00' END
FROM branches b CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),('THURSDAY'),('FRIDAY'),('SATURDAY'),('SUNDAY')) AS d(day)
WHERE b.id IN ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002');

INSERT INTO catalog_services (id, name, price, type) VALUES
('20000000-0000-0000-0000-000000000001', 'Corte básico', 100.00, 'HAIRCUT'),
('20000000-0000-0000-0000-000000000002', 'Corte escolar', 120.00, 'HAIRCUT'),
('20000000-0000-0000-0000-000000000003', 'Corte personalizado', 150.00, 'HAIRCUT'),
('20000000-0000-0000-0000-000000000004', 'Arreglo de barba', 50.00, 'ADD_ON'),
('20000000-0000-0000-0000-000000000005', 'Arreglo de ceja', 30.00, 'ADD_ON');
