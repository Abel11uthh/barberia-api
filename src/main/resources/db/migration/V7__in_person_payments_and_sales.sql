CREATE TABLE appointment_charges (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    actor_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    method VARCHAR(4) NOT NULL CHECK (method IN ('CASH','CARD')),
    paid_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX appointment_charges_period_idx ON appointment_charges(paid_at, branch_id);

-- A request row is inserted and locked inside the sale transaction. Failed sales
-- roll it back; concurrent retries wait for the successful response to become visible.
CREATE TABLE sale_operations (
    actor_id UUID NOT NULL REFERENCES accounts(id),
    idempotency_key UUID NOT NULL,
    PRIMARY KEY (actor_id, idempotency_key)
);
CREATE TABLE product_sales (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id),
    actor_id UUID NOT NULL REFERENCES accounts(id),
    idempotency_key UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    method VARCHAR(4) NOT NULL CHECK (method IN ('CASH','CARD')),
    total NUMERIC(38,2) NOT NULL CHECK (total > 0),
    paid_at TIMESTAMPTZ NOT NULL,
    UNIQUE (actor_id, idempotency_key),
    FOREIGN KEY (actor_id, idempotency_key) REFERENCES sale_operations(actor_id, idempotency_key)
);
CREATE INDEX product_sales_period_idx ON product_sales(paid_at, branch_id);
CREATE TABLE product_sale_lines (
    sale_id UUID NOT NULL REFERENCES product_sales(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    product_id UUID NOT NULL REFERENCES products(id),
    name VARCHAR(120) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (sale_id, position),
    UNIQUE (sale_id, product_id)
);

ALTER TABLE inventory_movements ADD COLUMN sale_id UUID REFERENCES product_sales(id);
ALTER TABLE inventory_movements DROP CONSTRAINT inventory_movements_kind_check;
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_kind_check
    CHECK (kind IN ('ENTRY','ADJUSTMENT','SALE'));
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_sale_link
    CHECK ((kind = 'SALE' AND sale_id IS NOT NULL AND delta < 0)
        OR (kind <> 'SALE' AND sale_id IS NULL));
CREATE UNIQUE INDEX inventory_movements_sale_product_idx ON inventory_movements(sale_id, product_id)
    WHERE sale_id IS NOT NULL;
