--liquibase formatted sql

-- changeset ngolahealth:episodes-001-create-episodes
-- comment: Episódios clínicos (consulta, urgência, exame…). Depois de concluído ou cancelado, o episódio fica só de leitura.
CREATE TABLE episodes (
    id                    CHAR(36)      NOT NULL,
    hospital_id           CHAR(36)      NOT NULL,
    patient_id            CHAR(36)      NOT NULL,
    doctor_id             CHAR(36)      NULL,
    type                  VARCHAR(30)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    scheduled_at          DATETIME(6)   NOT NULL,
    reason                VARCHAR(500)  NOT NULL COMMENT 'Motivo / queixa principal',
    symptoms              TEXT          NULL COMMENT 'Anamnese',
    diagnosis             TEXT          NULL,
    clinical_notes        TEXT          NULL COMMENT 'Exame objectivo, plano e outras notas',
    systolic_bp           INT           NULL,
    diastolic_bp          INT           NULL,
    heart_rate            INT           NULL,
    respiratory_rate      INT           NULL,
    temperature           DECIMAL(4, 1) NULL,
    oxygen_saturation     INT           NULL,
    weight_kg             DECIMAL(5, 2) NULL,
    height_cm             DECIMAL(5, 1) NULL,
    vitals_recorded_at    DATETIME(6)   NULL,
    vitals_recorded_by    CHAR(36)      NULL,
    started_at            DATETIME(6)   NULL,
    started_by            CHAR(36)      NULL,
    completed_at          DATETIME(6)   NULL,
    completed_by          CHAR(36)      NULL,
    cancelled_at          DATETIME(6)   NULL,
    cancelled_by          CHAR(36)      NULL,
    cancellation_reason   VARCHAR(500)  NULL,
    created_by            CHAR(36)      NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT pk_episodes PRIMARY KEY (id),
    CONSTRAINT fk_episodes_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_episodes_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_episodes_doctor FOREIGN KEY (doctor_id) REFERENCES users (id),
    CONSTRAINT ck_episodes_type CHECK (type IN ('EMERGENCY', 'OUTPATIENT', 'INPATIENT', 'OUTPATIENT_SURGERY', 'EXAM')),
    CONSTRAINT ck_episodes_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_episodes_hospital_status_time ON episodes (hospital_id, status, scheduled_at);
CREATE INDEX idx_episodes_patient_time ON episodes (patient_id, scheduled_at);
CREATE INDEX idx_episodes_doctor_time ON episodes (doctor_id, scheduled_at);
-- rollback DROP TABLE episodes;
