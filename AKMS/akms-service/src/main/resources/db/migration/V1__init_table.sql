-- V1__init.sql

CREATE TABLE if not exists customers (
    id BIGSERIAL PRIMARY KEY,        -- auto-incremented integer PK
    uuid UUID UNIQUE NOT NULL,       -- unique identifier
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE if not exists api_keys (
    id BIGSERIAL PRIMARY KEY,        -- auto-incremented integer PK
    uuid UUID UNIQUE NOT NULL,       -- unique identifier
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    api_key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    permissions TEXT, -- can hold JSON array as string
    rate_limit INT DEFAULT 1000,
    expiry_date TIMESTAMP,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
