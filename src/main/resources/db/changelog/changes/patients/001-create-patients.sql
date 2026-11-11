--liquibase formatted sql

-- changeset ngolahealth:patients-001-create-patients
-- comment: Pacientes de cada hospital. Identificadores (processo, BI, cartão de saúde) são únicos dentro do hospital.
CREATE TABLE patients (
    id                             CHAR(36)     NOT NULL,
    hospital_id                    CHAR(36)     NOT NULL,
    record_number                  VARCHAR(20)  NOT NULL COMMENT 'Número de processo, sequencial por hospital',
    full_name                      VARCHAR(200) NOT NULL,
    birth_date                     DATE         NOT NULL,
    gender                         VARCHAR(10)  NOT NULL,
    national_id                    VARCHAR(20)  NULL COMMENT 'BI ou NIF',
    health_card_number             VARCHAR(30)  NULL,
    phone                          VARCHAR(20)  NULL,
    email                          VARCHAR(200) NULL,
    address                        VARCHAR(500) NULL,
    province                       VARCHAR(100) NULL,
    municipality                   VARCHAR(100) NULL,
    emergency_contact_name         VARCHAR(200) NULL,
    emergency_contact_phone        VARCHAR(20)  NULL,
    emergency_contact_relationship VARCHAR(50)  NULL,
    blood_type                     VARCHAR(3)   NULL,
    allergies                      TEXT         NULL,
    chronic_conditions             TEXT         NULL,
    notes                          TEXT         NULL,
    active                         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by                     CHAR(36)     NULL,
    version                        BIGINT       NOT NULL DEFAULT 0,
    created_at                     DATETIME(6)  NOT NULL,
    updated_at                     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT fk_patients_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_patients_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uq_patients_hospital_record UNIQUE (hospital_id, record_number),
    CONSTRAINT uq_patients_hospital_national_id UNIQUE (hospital_id, national_id),
    CONSTRAINT uq_patients_hospital_health_card UNIQUE (hospital_id, health_card_number),
    CONSTRAINT ck_patients_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_patients_blood_type CHECK (blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_patients_hospital_active_name ON patients (hospital_id, active, full_name);
CREATE INDEX idx_patients_hospital_birth ON patients (hospital_id, birth_date);
CREATE INDEX idx_patients_hospital_phone ON patients (hospital_id, phone);
-- rollback DROP TABLE patients;

-- changeset ngolahealth:patients-002-create-record-sequences
-- comment: Contador do número de processo de cada hospital (incremento atómico com bloqueio de linha).
CREATE TABLE patient_record_sequences (
    hospital_id CHAR(36) NOT NULL,
    counter_value BIGINT   NOT NULL,
    CONSTRAINT pk_patient_record_sequences PRIMARY KEY (hospital_id),
    CONSTRAINT fk_patient_record_sequences_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE patient_record_sequences;
