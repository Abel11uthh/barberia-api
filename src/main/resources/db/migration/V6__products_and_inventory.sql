CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT product_sku_normalized CHECK (sku = upper(btrim(sku)) AND sku ~ '^[A-Z0-9][A-Z0-9._-]*$')
);
CREATE INDEX products_active_name_idx ON products(active, name, id);

CREATE TABLE product_stocks (
    product_id UUID NOT NULL REFERENCES products(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    PRIMARY KEY (product_id, branch_id)
);
CREATE INDEX product_stocks_branch_idx ON product_stocks(branch_id);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    actor_id UUID NOT NULL REFERENCES accounts(id),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('ENTRY','ADJUSTMENT')),
    delta BIGINT NOT NULL CHECK (delta <> 0),
    quantity_before BIGINT NOT NULL CHECK (quantity_before >= 0),
    quantity_after BIGINT NOT NULL CHECK (quantity_after >= 0),
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_movement_amount CHECK (quantity_after = quantity_before + delta),
    CONSTRAINT inventory_entry_positive CHECK (kind <> 'ENTRY' OR delta > 0)
);
CREATE INDEX inventory_movements_product_date_idx ON inventory_movements(product_id, occurred_at DESC, id);
CREATE INDEX inventory_movements_branch_date_idx ON inventory_movements(branch_id, occurred_at DESC, id);
