CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE supplier (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    phone      VARCHAR(30),
    email      VARCHAR(150),
    city       VARCHAR(80),
    vat_number VARCHAR(20),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE customer (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    phone      VARCHAR(30),
    email      VARCHAR(150),
    city       VARCHAR(80),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    sku           VARCHAR(40)    NOT NULL UNIQUE,
    name          VARCHAR(150)   NOT NULL,
    category_id   BIGINT         NOT NULL REFERENCES category (id),
    supplier_id   BIGINT         REFERENCES supplier (id),
    unit          VARCHAR(20)    NOT NULL,
    cost_price    NUMERIC(12, 2) NOT NULL CHECK (cost_price >= 0),
    sale_price    NUMERIC(12, 2) NOT NULL CHECK (sale_price >= 0),
    quantity      INTEGER        NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reorder_level INTEGER        NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_name ON product (lower(name));

CREATE TABLE purchase_order (
    id          BIGSERIAL PRIMARY KEY,
    number      VARCHAR(20)    NOT NULL UNIQUE,
    supplier_id BIGINT         NOT NULL REFERENCES supplier (id),
    status      VARCHAR(20)    NOT NULL,
    notes       VARCHAR(500),
    subtotal    NUMERIC(14, 2) NOT NULL,
    vat         NUMERIC(14, 2) NOT NULL,
    total       NUMERIC(14, 2) NOT NULL,
    created_by  VARCHAR(50)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    received_at TIMESTAMPTZ
);

CREATE TABLE purchase_order_line (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES product (id),
    quantity          INTEGER        NOT NULL CHECK (quantity > 0),
    unit_cost         NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    line_total        NUMERIC(14, 2) NOT NULL
);

CREATE TABLE sales_order (
    id           BIGSERIAL PRIMARY KEY,
    number       VARCHAR(20)    NOT NULL UNIQUE,
    customer_id  BIGINT         NOT NULL REFERENCES customer (id),
    status       VARCHAR(20)    NOT NULL,
    notes        VARCHAR(500),
    subtotal     NUMERIC(14, 2) NOT NULL,
    vat          NUMERIC(14, 2) NOT NULL,
    total        NUMERIC(14, 2) NOT NULL,
    created_by   VARCHAR(50)    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE sales_order_line (
    id             BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT         NOT NULL REFERENCES sales_order (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES product (id),
    quantity       INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    line_total     NUMERIC(14, 2) NOT NULL
);

CREATE TABLE stock_movement (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES product (id),
    type          VARCHAR(20)  NOT NULL,
    quantity      INTEGER      NOT NULL,
    balance_after INTEGER      NOT NULL,
    reference     VARCHAR(30),
    note          VARCHAR(255),
    created_by    VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_movement_product ON stock_movement (product_id, created_at DESC);
CREATE INDEX idx_movement_created ON stock_movement (created_at DESC);

CREATE SEQUENCE purchase_order_number_seq;
CREATE SEQUENCE sales_order_number_seq;
