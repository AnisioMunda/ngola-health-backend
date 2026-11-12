--liquibase formatted sql

-- changeset ngolahealth:prescriptions-001-create-prescriptions
-- comment: Prescrições médicas, emitidas num episódio. O estado EXPIRED é calculado (valid_until), não gravado.
CREATE TABLE prescriptions (
    id                  CHAR(36)     NOT NULL,
    hospital_id         CHAR(36)     NOT NULL,
    number              VARCHAR(20)  NOT NULL COMMENT 'RX-AAAA-NNNNNN, sequencial por hospital e ano',
    patient_id          CHAR(36)     NOT NULL,
    episode_id          CHAR(36)     NOT NULL,
    prescriber_id       CHAR(36)     NOT NULL,
    status              VARCHAR(25)  NOT NULL,
    issued_at           DATETIME(6)  NOT NULL,
    valid_until         DATE         NOT NULL,
    notes               TEXT         NULL,
    cancelled_at        DATETIME(6)  NULL,
    cancelled_by        CHAR(36)     NULL,
    cancellation_reason VARCHAR(500) NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_prescriptions PRIMARY KEY (id),
    CONSTRAINT uq_prescriptions_number UNIQUE (hospital_id, number),
    CONSTRAINT fk_prescriptions_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_prescriptions_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_prescriptions_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_prescriptions_prescriber FOREIGN KEY (prescriber_id) REFERENCES users (id),
    CONSTRAINT ck_prescriptions_status CHECK (status IN ('ACTIVE', 'PARTIALLY_DISPENSED', 'DISPENSED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_prescriptions_hospital_status ON prescriptions (hospital_id, status, issued_at);
CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id, issued_at);
CREATE INDEX idx_prescriptions_episode ON prescriptions (episode_id);
-- rollback DROP TABLE prescriptions;

-- changeset ngolahealth:prescriptions-002-create-items
CREATE TABLE prescription_items (
    id                 CHAR(36)     NOT NULL,
    hospital_id        CHAR(36)     NOT NULL,
    prescription_id    CHAR(36)     NOT NULL,
    line_number        INT          NOT NULL,
    medication_id      CHAR(36)     NOT NULL,
    quantity           INT          NOT NULL,
    quantity_dispensed INT          NOT NULL DEFAULT 0,
    dosage             VARCHAR(200) NOT NULL COMMENT 'Ex.: 1 comprimido',
    frequency          VARCHAR(100) NOT NULL COMMENT 'Ex.: de 8 em 8 horas',
    duration_days      INT          NULL,
    route              VARCHAR(20)  NOT NULL,
    instructions       VARCHAR(500) NULL,
    CONSTRAINT pk_prescription_items PRIMARY KEY (id),
    CONSTRAINT fk_prescription_items_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_prescription_items_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id),
    CONSTRAINT fk_prescription_items_medication FOREIGN KEY (medication_id) REFERENCES medications (id),
    CONSTRAINT uq_prescription_items_medication UNIQUE (prescription_id, medication_id),
    CONSTRAINT ck_prescription_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_prescription_items_dispensed CHECK (quantity_dispensed BETWEEN 0 AND quantity),
    CONSTRAINT ck_prescription_items_duration CHECK (duration_days IS NULL OR duration_days BETWEEN 1 AND 365),
    CONSTRAINT ck_prescription_items_route CHECK (route IN ('ORAL', 'SUBLINGUAL', 'INTRAVENOUS', 'INTRAMUSCULAR',
        'SUBCUTANEOUS', 'TOPICAL', 'INHALED', 'RECTAL', 'OPHTHALMIC', 'OTIC', 'NASAL', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_prescription_items_prescription ON prescription_items (prescription_id, line_number);
-- rollback DROP TABLE prescription_items;

-- changeset ngolahealth:prescriptions-003-create-sequences
CREATE TABLE prescription_sequences (
    hospital_id   CHAR(36) NOT NULL,
    year          INT      NOT NULL,
    counter_value BIGINT   NOT NULL,
    CONSTRAINT pk_prescription_sequences PRIMARY KEY (hospital_id, year),
    CONSTRAINT fk_prescription_sequences_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE prescription_sequences;
