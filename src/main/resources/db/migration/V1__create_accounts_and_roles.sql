CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT accounts_email_key UNIQUE (email),
    CONSTRAINT accounts_email_normalized CHECK (email = lower(btrim(email)))
);

CREATE TABLE roles (
    name VARCHAR(16) PRIMARY KEY,
    CONSTRAINT roles_allowed CHECK (name IN ('CLIENT', 'BARBER', 'ADMIN'))
);

INSERT INTO roles (name) VALUES ('CLIENT'), ('BARBER'), ('ADMIN');

CREATE TABLE account_roles (
    account_id UUID NOT NULL REFERENCES accounts(id),
    role VARCHAR(16) NOT NULL REFERENCES roles(name),
    PRIMARY KEY (account_id, role)
);
