--liquibase formatted sql

-- changeset ngolahealth:financial-001-create-service-prices
-- comment: Tabela de preços (actos, exames, diárias…) de cada hospital. Valores em AOA.
CREATE TABLE service_prices (
    id                   CHAR(36)      NOT NULL,
    hospital_id          CHAR(36)      NOT NULL,
    code                 VARCHAR(30)   NOT NULL,
    description          VARCHAR(300)  NOT NULL,
    category             VARCHAR(20)   NOT NULL,
    unit_price           DECIMAL(14,2) NOT NULL,
    vat_rate             DECIMAL(5,2)  NOT NULL,
    vat_exemption_reason VARCHAR(200)  NULL COMMENT 'Obrigatório quando a taxa de IVA é 0',
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_service_prices PRIMARY KEY (id),
    CONSTRAINT fk_service_prices_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT uq_service_prices_code UNIQUE (hospital_id, code),
    CONSTRAINT ck_service_prices_category CHECK (category IN ('CONSULTATION', 'EXAM', 'LAB', 'IMAGING',
        'PROCEDURE', 'SURGERY', 'ACCOMMODATION', 'PHARMACY', 'OTHER')),
    CONSTRAINT ck_service_prices_price CHECK (unit_price >= 0),
    CONSTRAINT ck_service_prices_vat CHECK (vat_rate >= 0 AND vat_rate <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE service_prices;

-- changeset ngolahealth:financial-002-create-invoices
-- comment: Documentos de facturação: FT (factura), FR (factura-recibo), NC (nota de crédito).
-- comment: O número só é atribuído na emissão (rascunhos não têm número): sequência sem falhas por
-- comment: hospital, tipo e ano. Depois de emitido, o documento não se altera.
CREATE TABLE invoices (
    id                  CHAR(36)      NOT NULL,
    hospital_id         CHAR(36)      NOT NULL,
    document_type       VARCHAR(2)    NOT NULL,
    series_year         INT           NULL,
    sequence_number     BIGINT        NULL,
    number              VARCHAR(30)   NULL,
    status              VARCHAR(20)   NOT NULL,
    patient_id          CHAR(36)      NOT NULL,
    episode_id          CHAR(36)      NULL,
    customer_name       VARCHAR(200)  NOT NULL,
    customer_tax_id     VARCHAR(20)   NULL COMMENT 'NIF; vazio = consumidor final',
    customer_address    VARCHAR(300)  NULL,
    issuer_name         VARCHAR(200)  NULL COMMENT 'Copiado do hospital na emissão',
    issuer_tax_id       VARCHAR(20)   NULL,
    issuer_address      VARCHAR(300)  NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'AOA',
    net_total           DECIMAL(14,2) NOT NULL DEFAULT 0,
    vat_total           DECIMAL(14,2) NOT NULL DEFAULT 0,
    gross_total         DECIMAL(14,2) NOT NULL DEFAULT 0,
    paid_total          DECIMAL(14,2) NOT NULL DEFAULT 0,
    due_date            DATE          NULL,
    notes               VARCHAR(1000) NULL,
    related_invoice_id  CHAR(36)      NULL COMMENT 'Na nota de crédito: documento que corrige',
    credit_reason       VARCHAR(500)  NULL,
    created_by          CHAR(36)      NOT NULL,
    issued_by           CHAR(36)      NULL,
    issued_at           DATETIME(6)   NULL,
    cancelled_by        CHAR(36)      NULL,
    cancelled_at        DATETIME(6)   NULL,
    cancellation_reason VARCHAR(500)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT fk_invoices_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_invoices_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_invoices_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_invoices_related FOREIGN KEY (related_invoice_id) REFERENCES invoices (id),
    CONSTRAINT uq_invoices_number UNIQUE (hospital_id, number),
    CONSTRAINT uq_invoices_sequence UNIQUE (hospital_id, document_type, series_year, sequence_number),
    CONSTRAINT ck_invoices_type CHECK (document_type IN ('FT', 'FR', 'NC')),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID',
        'CANCELLED', 'CREDITED')),
    CONSTRAINT ck_invoices_currency CHECK (currency = 'AOA'),
    CONSTRAINT ck_invoices_totals CHECK (net_total >= 0 AND vat_total >= 0 AND gross_total >= 0
        AND paid_total >= 0 AND paid_total <= gross_total),
    CONSTRAINT ck_invoices_numbered CHECK (status = 'DRAFT' OR number IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_invoices_status ON invoices (hospital_id, status, created_at);
CREATE INDEX idx_invoices_patient ON invoices (patient_id, created_at);
CREATE INDEX idx_invoices_issued ON invoices (hospital_id, issued_at);
-- rollback DROP TABLE invoices;

-- changeset ngolahealth:financial-003-create-invoice-items
CREATE TABLE invoice_items (
    id                   CHAR(36)      NOT NULL,
    hospital_id          CHAR(36)      NOT NULL,
    invoice_id           CHAR(36)      NOT NULL,
    line_number          INT           NOT NULL,
    service_price_id     CHAR(36)      NULL,
    code                 VARCHAR(30)   NULL,
    description          VARCHAR(300)  NOT NULL,
    quantity             DECIMAL(10,2) NOT NULL,
    unit_price           DECIMAL(14,2) NOT NULL,
    discount_percent     DECIMAL(5,2)  NOT NULL DEFAULT 0,
    vat_rate             DECIMAL(5,2)  NOT NULL,
    vat_exemption_reason VARCHAR(200)  NULL,
    discount_amount      DECIMAL(14,2) NOT NULL,
    net_amount           DECIMAL(14,2) NOT NULL,
    vat_amount           DECIMAL(14,2) NOT NULL,
    gross_amount         DECIMAL(14,2) NOT NULL,
    CONSTRAINT pk_invoice_items PRIMARY KEY (id),
    CONSTRAINT fk_invoice_items_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_price FOREIGN KEY (service_price_id) REFERENCES service_prices (id),
    CONSTRAINT uq_invoice_items_line UNIQUE (invoice_id, line_number),
    CONSTRAINT ck_invoice_items_values CHECK (quantity > 0 AND unit_price >= 0
        AND discount_percent >= 0 AND discount_percent <= 100 AND vat_rate >= 0 AND vat_rate <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE invoice_items;

-- changeset ngolahealth:financial-004-create-payments
-- comment: Pagamentos; cada um tem o seu recibo (RC), numerado como os outros documentos.
CREATE TABLE payments (
    id             CHAR(36)      NOT NULL,
    hospital_id    CHAR(36)      NOT NULL,
    invoice_id     CHAR(36)      NOT NULL,
    receipt_number VARCHAR(30)   NOT NULL,
    amount         DECIMAL(14,2) NOT NULL,
    method         VARCHAR(20)   NOT NULL,
    reference      VARCHAR(100)  NULL COMMENT 'Nº do talão TPA, transferência, Multicaixa Express…',
    received_by    CHAR(36)      NOT NULL,
    received_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT uq_payments_receipt UNIQUE (hospital_id, receipt_number),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD', 'MULTICAIXA_EXPRESS', 'TRANSFER', 'CHEQUE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_payments_invoice ON payments (invoice_id);
CREATE INDEX idx_payments_received ON payments (hospital_id, received_at);
-- rollback DROP TABLE payments;

-- changeset ngolahealth:financial-005-create-document-sequences
-- comment: Contadores por hospital, tipo de documento (FT, FR, NC, RC) e ano. Actualizados na mesma
-- comment: transacção da emissão: se a emissão falhar, o número não se perde.
CREATE TABLE document_sequences (
    hospital_id   CHAR(36)   NOT NULL,
    document_type VARCHAR(2) NOT NULL,
    year          INT        NOT NULL,
    counter_value BIGINT     NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (hospital_id, document_type, year),
    CONSTRAINT fk_document_sequences_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE document_sequences;
