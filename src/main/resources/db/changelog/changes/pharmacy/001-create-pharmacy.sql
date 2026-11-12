--liquibase formatted sql

-- changeset ngolahealth:pharmacy-001-create-medications
-- comment: Catálogo de medicamentos de cada hospital.
CREATE TABLE medications (
    id                    CHAR(36)     NOT NULL,
    hospital_id           CHAR(36)     NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    generic_name          VARCHAR(200) NULL COMMENT 'Denominação comum internacional (DCI)',
    dosage_form           VARCHAR(20)  NOT NULL,
    strength              VARCHAR(50)  NULL COMMENT 'Ex.: 500 mg, 250 mg/5 ml',
    dispensing_unit       VARCHAR(30)  NOT NULL COMMENT 'Ex.: comprimido, frasco, ampola',
    requires_prescription BOOLEAN      NOT NULL DEFAULT TRUE,
    min_stock_level       INT          NOT NULL DEFAULT 0,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT pk_medications PRIMARY KEY (id),
    CONSTRAINT fk_medications_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT uq_medications_identity UNIQUE (hospital_id, name, dosage_form, strength),
    CONSTRAINT ck_medications_min_stock CHECK (min_stock_level >= 0),
    CONSTRAINT ck_medications_form CHECK (dosage_form IN ('TABLET', 'CAPSULE', 'SYRUP', 'SUSPENSION',
        'SOLUTION', 'INJECTION', 'CREAM', 'OINTMENT', 'DROPS', 'INHALER', 'SUPPOSITORY', 'POWDER', 'PATCH', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_medications_hospital_name ON medications (hospital_id, active, name);
-- rollback DROP TABLE medications;

-- changeset ngolahealth:pharmacy-002-create-stock-batches
-- comment: Lotes recebidos. O saldo nunca pode ser negativo (CHECK + bloqueio na dispensa).
CREATE TABLE stock_batches (
    id                 CHAR(36)      NOT NULL,
    hospital_id        CHAR(36)      NOT NULL,
    medication_id      CHAR(36)      NOT NULL,
    batch_number       VARCHAR(50)   NOT NULL,
    expiry_date        DATE          NOT NULL,
    quantity_received  INT           NOT NULL,
    quantity_available INT           NOT NULL,
    unit_cost          DECIMAL(12, 2) NULL COMMENT 'Custo unitário em AOA',
    supplier           VARCHAR(200)  NULL,
    received_at        DATETIME(6)   NOT NULL,
    received_by        CHAR(36)      NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_batches PRIMARY KEY (id),
    CONSTRAINT fk_stock_batches_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_stock_batches_medication FOREIGN KEY (medication_id) REFERENCES medications (id),
    CONSTRAINT fk_stock_batches_received_by FOREIGN KEY (received_by) REFERENCES users (id),
    CONSTRAINT uq_stock_batches_number UNIQUE (hospital_id, medication_id, batch_number),
    CONSTRAINT ck_stock_batches_received CHECK (quantity_received > 0),
    CONSTRAINT ck_stock_batches_available CHECK (quantity_available >= 0),
    CONSTRAINT ck_stock_batches_cost CHECK (unit_cost IS NULL OR unit_cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_stock_batches_fefo ON stock_batches (medication_id, expiry_date);
CREATE INDEX idx_stock_batches_hospital_expiry ON stock_batches (hospital_id, expiry_date);
-- rollback DROP TABLE stock_batches;

-- changeset ngolahealth:pharmacy-003-create-stock-movements
-- comment: Livro de movimentos de stock (só inserção). quantity com sinal; balance_after = saldo do lote após o movimento.
CREATE TABLE stock_movements (
    id              CHAR(36)     NOT NULL,
    hospital_id     CHAR(36)     NOT NULL,
    medication_id   CHAR(36)     NOT NULL,
    batch_id        CHAR(36)     NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    quantity        INT          NOT NULL,
    balance_after   INT          NOT NULL,
    patient_id      CHAR(36)     NULL,
    episode_id      CHAR(36)     NULL,
    reference_id    CHAR(36)     NULL COMMENT 'Ex.: prescrição que originou a dispensa',
    reason          VARCHAR(500) NULL,
    performed_by    CHAR(36)     NOT NULL,
    performed_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_stock_movements_medication FOREIGN KEY (medication_id) REFERENCES medications (id),
    CONSTRAINT fk_stock_movements_batch FOREIGN KEY (batch_id) REFERENCES stock_batches (id),
    CONSTRAINT fk_stock_movements_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_stock_movements_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (performed_by) REFERENCES users (id),
    CONSTRAINT ck_stock_movements_type CHECK (type IN ('RECEIPT', 'DISPENSE', 'ADJUSTMENT_IN',
        'ADJUSTMENT_OUT', 'WRITE_OFF_EXPIRED', 'WRITE_OFF_DAMAGED')),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity <> 0),
    CONSTRAINT ck_stock_movements_balance CHECK (balance_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_stock_movements_medication_time ON stock_movements (medication_id, performed_at);
CREATE INDEX idx_stock_movements_batch ON stock_movements (batch_id);
-- rollback DROP TABLE stock_movements;
