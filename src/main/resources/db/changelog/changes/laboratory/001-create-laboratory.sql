--liquibase formatted sql

-- changeset ngolahealth:laboratory-001-create-lab-tests
-- comment: Catálogo de exames do laboratório de cada hospital.
CREATE TABLE lab_tests (
    id               CHAR(36)       NOT NULL,
    hospital_id      CHAR(36)       NOT NULL,
    code             VARCHAR(30)    NOT NULL,
    name             VARCHAR(200)   NOT NULL,
    category         VARCHAR(20)    NOT NULL,
    sample_type      VARCHAR(20)    NOT NULL,
    result_type      VARCHAR(15)    NOT NULL COMMENT 'NUMERIC (valor + unidade + intervalo) ou QUALITATIVE (texto)',
    unit             VARCHAR(30)    NULL,
    reference_low    DECIMAL(12, 4) NULL,
    reference_high   DECIMAL(12, 4) NULL,
    reference_text   VARCHAR(200)   NULL COMMENT 'Ex.: Negativo',
    turnaround_hours INT            NOT NULL DEFAULT 24,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    CONSTRAINT pk_lab_tests PRIMARY KEY (id),
    CONSTRAINT fk_lab_tests_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT uq_lab_tests_code UNIQUE (hospital_id, code),
    CONSTRAINT ck_lab_tests_category CHECK (category IN ('HEMATOLOGY', 'BIOCHEMISTRY', 'MICROBIOLOGY',
        'PARASITOLOGY', 'IMMUNOLOGY', 'SEROLOGY', 'URINALYSIS', 'OTHER')),
    CONSTRAINT ck_lab_tests_sample CHECK (sample_type IN ('BLOOD', 'SERUM', 'PLASMA', 'URINE', 'STOOL',
        'SWAB', 'SPUTUM', 'CSF', 'OTHER')),
    CONSTRAINT ck_lab_tests_result_type CHECK (result_type IN ('NUMERIC', 'QUALITATIVE')),
    CONSTRAINT ck_lab_tests_range CHECK (reference_low IS NULL OR reference_high IS NULL OR reference_low <= reference_high),
    CONSTRAINT ck_lab_tests_turnaround CHECK (turnaround_hours BETWEEN 1 AND 2160)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_lab_tests_hospital_name ON lab_tests (hospital_id, active, name);
-- rollback DROP TABLE lab_tests;

-- changeset ngolahealth:laboratory-002-create-lab-requests
-- comment: Pedidos de exames, emitidos num episódio.
CREATE TABLE lab_requests (
    id                   CHAR(36)     NOT NULL,
    hospital_id          CHAR(36)     NOT NULL,
    number               VARCHAR(20)  NOT NULL COMMENT 'LAB-AAAA-NNNNNN, por hospital e ano',
    patient_id           CHAR(36)     NOT NULL,
    episode_id           CHAR(36)     NOT NULL,
    requested_by         CHAR(36)     NOT NULL,
    requested_at         DATETIME(6)  NOT NULL,
    priority             VARCHAR(10)  NOT NULL,
    clinical_info        TEXT         NULL,
    status               VARCHAR(15)  NOT NULL,
    collected_at         DATETIME(6)  NULL,
    collected_by         CHAR(36)     NULL,
    collection_notes     VARCHAR(500) NULL,
    analysis_started_at  DATETIME(6)  NULL,
    completed_at         DATETIME(6)  NULL,
    cancelled_at         DATETIME(6)  NULL,
    cancelled_by         CHAR(36)     NULL,
    cancellation_reason  VARCHAR(500) NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT pk_lab_requests PRIMARY KEY (id),
    CONSTRAINT uq_lab_requests_number UNIQUE (hospital_id, number),
    CONSTRAINT fk_lab_requests_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_lab_requests_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_lab_requests_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_lab_requests_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_lab_requests_priority CHECK (priority IN ('ROUTINE', 'URGENT')),
    CONSTRAINT ck_lab_requests_status CHECK (status IN ('PENDING', 'COLLECTED', 'IN_ANALYSIS', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_lab_requests_hospital_status ON lab_requests (hospital_id, status, requested_at);
CREATE INDEX idx_lab_requests_patient ON lab_requests (patient_id, requested_at);
CREATE INDEX idx_lab_requests_episode ON lab_requests (episode_id);
-- rollback DROP TABLE lab_requests;

-- changeset ngolahealth:laboratory-003-create-lab-request-items
-- comment: Exames de um pedido. Os valores de referência são copiados do catálogo no momento do pedido.
CREATE TABLE lab_request_items (
    id               CHAR(36)       NOT NULL,
    hospital_id      CHAR(36)       NOT NULL,
    request_id       CHAR(36)       NOT NULL,
    lab_test_id      CHAR(36)       NOT NULL,
    test_code        VARCHAR(30)    NOT NULL,
    test_name        VARCHAR(200)   NOT NULL,
    result_type      VARCHAR(15)    NOT NULL,
    unit             VARCHAR(30)    NULL,
    reference_low    DECIMAL(12, 4) NULL,
    reference_high   DECIMAL(12, 4) NULL,
    reference_text   VARCHAR(200)   NULL,
    status           VARCHAR(15)    NOT NULL,
    numeric_value    DECIMAL(12, 4) NULL,
    text_value       VARCHAR(500)   NULL,
    flag             VARCHAR(10)    NULL,
    result_notes     VARCHAR(1000)  NULL,
    resulted_at      DATETIME(6)    NULL,
    resulted_by      CHAR(36)       NULL,
    validated_at     DATETIME(6)    NULL,
    validated_by     CHAR(36)       NULL,
    amended          BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_lab_request_items PRIMARY KEY (id),
    CONSTRAINT fk_lab_items_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_lab_items_request FOREIGN KEY (request_id) REFERENCES lab_requests (id),
    CONSTRAINT fk_lab_items_test FOREIGN KEY (lab_test_id) REFERENCES lab_tests (id),
    CONSTRAINT uq_lab_items_test UNIQUE (request_id, lab_test_id),
    CONSTRAINT ck_lab_items_status CHECK (status IN ('PENDING', 'RESULTED', 'VALIDATED', 'CANCELLED')),
    CONSTRAINT ck_lab_items_flag CHECK (flag IS NULL OR flag IN ('NORMAL', 'LOW', 'HIGH', 'ABNORMAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_lab_items_request ON lab_request_items (request_id);
-- rollback DROP TABLE lab_request_items;

-- changeset ngolahealth:laboratory-004-create-result-amendments
-- comment: Correcções de resultados já validados (valor anterior, novo valor, motivo, quem e quando).
CREATE TABLE lab_result_amendments (
    id                 CHAR(36)       NOT NULL,
    hospital_id        CHAR(36)       NOT NULL,
    item_id            CHAR(36)       NOT NULL,
    previous_numeric   DECIMAL(12, 4) NULL,
    previous_text      VARCHAR(500)   NULL,
    previous_flag      VARCHAR(10)    NULL,
    new_numeric        DECIMAL(12, 4) NULL,
    new_text           VARCHAR(500)   NULL,
    new_flag           VARCHAR(10)    NULL,
    reason             VARCHAR(500)   NOT NULL,
    amended_by         CHAR(36)       NOT NULL,
    amended_at         DATETIME(6)    NOT NULL,
    CONSTRAINT pk_lab_result_amendments PRIMARY KEY (id),
    CONSTRAINT fk_lab_amend_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_lab_amend_item FOREIGN KEY (item_id) REFERENCES lab_request_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_lab_amend_item ON lab_result_amendments (item_id, amended_at);
-- rollback DROP TABLE lab_result_amendments;

-- changeset ngolahealth:laboratory-005-create-sequences
CREATE TABLE lab_request_sequences (
    hospital_id   CHAR(36) NOT NULL,
    year          INT      NOT NULL,
    counter_value BIGINT   NOT NULL,
    CONSTRAINT pk_lab_request_sequences PRIMARY KEY (hospital_id, year),
    CONSTRAINT fk_lab_request_sequences_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE lab_request_sequences;
