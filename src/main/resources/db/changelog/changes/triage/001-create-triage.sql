--liquibase formatted sql

-- changeset ngolahealth:triage-001-create-triage-records
-- comment: Triagem de Manchester. Doente registado (patient_id) ou temporário (temp_*), identificável mais tarde.
CREATE TABLE triage_records (
    id                  CHAR(36)      NOT NULL,
    hospital_id         CHAR(36)      NOT NULL,
    queue_date          DATE          NOT NULL COMMENT 'Dia (hora local) a que pertence a senha',
    queue_number        INT           NOT NULL,
    patient_id          CHAR(36)      NULL,
    temp_name           VARCHAR(200)  NULL,
    temp_age            INT           NULL,
    temp_gender         VARCHAR(10)   NULL,
    priority            VARCHAR(10)   NOT NULL,
    chief_complaint     VARCHAR(500)  NOT NULL,
    pain_scale          INT           NULL,
    notes               TEXT          NULL,
    systolic_bp         INT           NULL,
    diastolic_bp        INT           NULL,
    heart_rate          INT           NULL,
    respiratory_rate    INT           NULL,
    temperature         DECIMAL(4, 1) NULL,
    oxygen_saturation   INT           NULL,
    weight_kg           DECIMAL(5, 2) NULL,
    height_cm           DECIMAL(5, 1) NULL,
    status              VARCHAR(15)   NOT NULL,
    triaged_at          DATETIME(6)   NOT NULL,
    triaged_by          CHAR(36)      NOT NULL,
    called_at           DATETIME(6)   NULL,
    called_by           CHAR(36)      NULL,
    closed_at           DATETIME(6)   NULL,
    closed_by           CHAR(36)      NULL,
    episode_id          CHAR(36)      NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_triage_records PRIMARY KEY (id),
    CONSTRAINT uq_triage_queue UNIQUE (hospital_id, queue_date, queue_number),
    CONSTRAINT fk_triage_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_triage_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_triage_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_triage_triaged_by FOREIGN KEY (triaged_by) REFERENCES users (id),
    CONSTRAINT ck_triage_priority CHECK (priority IN ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE')),
    CONSTRAINT ck_triage_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'LEFT')),
    CONSTRAINT ck_triage_pain CHECK (pain_scale IS NULL OR pain_scale BETWEEN 0 AND 10),
    CONSTRAINT ck_triage_identity CHECK (patient_id IS NOT NULL OR temp_name IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_triage_hospital_status ON triage_records (hospital_id, status, triaged_at);
CREATE INDEX idx_triage_patient ON triage_records (patient_id);
-- rollback DROP TABLE triage_records;

-- changeset ngolahealth:triage-002-create-priority-changes
-- comment: Histórico de re-triagens (alterações de prioridade), com motivo obrigatório.
CREATE TABLE triage_priority_changes (
    id               CHAR(36)     NOT NULL,
    hospital_id      CHAR(36)     NOT NULL,
    triage_id        CHAR(36)     NOT NULL,
    from_priority    VARCHAR(10)  NOT NULL,
    to_priority      VARCHAR(10)  NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    changed_by       CHAR(36)     NOT NULL,
    changed_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_triage_priority_changes PRIMARY KEY (id),
    CONSTRAINT fk_triage_pc_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_triage_pc_triage FOREIGN KEY (triage_id) REFERENCES triage_records (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_triage_pc_triage ON triage_priority_changes (triage_id, changed_at);
-- rollback DROP TABLE triage_priority_changes;

-- changeset ngolahealth:triage-003-create-queue-sequences
-- comment: Contador da senha de triagem por hospital e por dia.
CREATE TABLE triage_queue_sequences (
    hospital_id   CHAR(36) NOT NULL,
    queue_date    DATE     NOT NULL,
    counter_value BIGINT   NOT NULL,
    CONSTRAINT pk_triage_queue_sequences PRIMARY KEY (hospital_id, queue_date),
    CONSTRAINT fk_triage_qs_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE triage_queue_sequences;
