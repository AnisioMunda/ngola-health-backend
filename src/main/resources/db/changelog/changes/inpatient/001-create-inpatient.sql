--liquibase formatted sql

-- changeset ngolahealth:inpatient-001-create-wards
-- comment: Enfermarias. O número de camas não se guarda: conta-se a partir das camas.
CREATE TABLE wards (
    id            CHAR(36)     NOT NULL,
    hospital_id   CHAR(36)     NOT NULL,
    code          VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    gender_policy VARCHAR(10)  NOT NULL COMMENT 'MIXED, MALE ou FEMALE',
    floor         VARCHAR(20)  NULL,
    notes         VARCHAR(500) NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_wards PRIMARY KEY (id),
    CONSTRAINT fk_wards_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT uq_wards_code UNIQUE (hospital_id, code),
    CONSTRAINT ck_wards_type CHECK (type IN ('GENERAL', 'MEDICINE', 'SURGERY', 'PEDIATRICS', 'MATERNITY',
        'ICU', 'ISOLATION', 'OTHER')),
    CONSTRAINT ck_wards_gender CHECK (gender_policy IN ('MIXED', 'MALE', 'FEMALE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE wards;

-- changeset ngolahealth:inpatient-002-create-beds
-- comment: Camas. OCCUPIED só é posto e retirado pelo ciclo do internamento.
CREATE TABLE beds (
    id          CHAR(36)     NOT NULL,
    hospital_id CHAR(36)     NOT NULL,
    ward_id     CHAR(36)     NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    type        VARCHAR(15)  NOT NULL,
    status      VARCHAR(15)  NOT NULL,
    status_note VARCHAR(200) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_beds PRIMARY KEY (id),
    CONSTRAINT fk_beds_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_beds_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT uq_beds_code UNIQUE (ward_id, code),
    CONSTRAINT ck_beds_type CHECK (type IN ('STANDARD', 'ICU', 'ISOLATION', 'CRIB')),
    CONSTRAINT ck_beds_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_beds_ward ON beds (ward_id, active, code);
-- rollback DROP TABLE beds;

-- changeset ngolahealth:inpatient-003-create-admissions
-- comment: Internamentos. Os índices únicos sobre colunas geradas (preenchidas só quando ACTIVE)
-- comment: garantem na BD uma cama = um internamento activo e um paciente = um internamento activo.
CREATE TABLE admissions (
    id                      CHAR(36)      NOT NULL,
    hospital_id             CHAR(36)      NOT NULL,
    number                  VARCHAR(20)   NOT NULL,
    patient_id              CHAR(36)      NOT NULL,
    episode_id              CHAR(36)      NOT NULL COMMENT 'Episódio de internamento (prescrições, exames, notas)',
    origin_episode_id       CHAR(36)      NULL COMMENT 'Episódio de onde veio (urgência, consulta)',
    ward_id                 CHAR(36)      NOT NULL,
    bed_id                  CHAR(36)      NOT NULL,
    responsible_doctor_id   CHAR(36)      NOT NULL,
    status                  VARCHAR(15)   NOT NULL,
    reason                  VARCHAR(500)  NOT NULL,
    admitted_by             CHAR(36)      NOT NULL,
    admitted_at             DATETIME(6)   NOT NULL,
    expected_discharge_date DATE          NULL,
    discharged_by           CHAR(36)      NULL,
    discharged_at           DATETIME(6)   NULL,
    discharge_condition     VARCHAR(15)   NULL,
    discharge_diagnosis     VARCHAR(4000) NULL,
    discharge_summary       TEXT          NULL,
    cancelled_by            CHAR(36)      NULL,
    cancelled_at            DATETIME(6)   NULL,
    cancellation_reason     VARCHAR(500)  NULL,
    active_bed_id           CHAR(36) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN bed_id END) STORED,
    active_patient_id       CHAR(36) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN patient_id END) STORED,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    CONSTRAINT pk_admissions PRIMARY KEY (id),
    CONSTRAINT fk_admissions_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_admissions_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_admissions_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_admissions_origin FOREIGN KEY (origin_episode_id) REFERENCES episodes (id),
    CONSTRAINT fk_admissions_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT fk_admissions_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    CONSTRAINT fk_admissions_doctor FOREIGN KEY (responsible_doctor_id) REFERENCES users (id),
    CONSTRAINT uq_admissions_number UNIQUE (hospital_id, number),
    CONSTRAINT uq_admissions_episode UNIQUE (episode_id),
    CONSTRAINT uq_admissions_active_bed UNIQUE (active_bed_id),
    CONSTRAINT uq_admissions_active_patient UNIQUE (active_patient_id),
    CONSTRAINT ck_admissions_status CHECK (status IN ('ACTIVE', 'DISCHARGED', 'CANCELLED')),
    CONSTRAINT ck_admissions_condition CHECK (discharge_condition IS NULL
        OR discharge_condition IN ('IMPROVED', 'STABLE', 'WORSENED', 'DECEASED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_admissions_status ON admissions (hospital_id, status, admitted_at);
CREATE INDEX idx_admissions_patient ON admissions (patient_id, admitted_at);
-- rollback DROP TABLE admissions;

-- changeset ngolahealth:inpatient-004-create-transfers
-- comment: Histórico imutável de mudanças de cama/enfermaria.
CREATE TABLE admission_transfers (
    id             CHAR(36)     NOT NULL,
    hospital_id    CHAR(36)     NOT NULL,
    admission_id   CHAR(36)     NOT NULL,
    from_ward_id   CHAR(36)     NOT NULL,
    from_bed_id    CHAR(36)     NOT NULL,
    to_ward_id     CHAR(36)     NOT NULL,
    to_bed_id      CHAR(36)     NOT NULL,
    reason         VARCHAR(300) NOT NULL,
    transferred_by CHAR(36)     NOT NULL,
    transferred_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_admission_transfers PRIMARY KEY (id),
    CONSTRAINT fk_admission_transfers_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_admission_transfers_admission FOREIGN KEY (admission_id) REFERENCES admissions (id),
    CONSTRAINT fk_admission_transfers_from_bed FOREIGN KEY (from_bed_id) REFERENCES beds (id),
    CONSTRAINT fk_admission_transfers_to_bed FOREIGN KEY (to_bed_id) REFERENCES beds (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_admission_transfers_admission ON admission_transfers (admission_id, transferred_at);
-- rollback DROP TABLE admission_transfers;

-- changeset ngolahealth:inpatient-005-create-admission-sequences
-- comment: Numeração INT-AAAA-NNNNNN por hospital e ano.
CREATE TABLE admission_sequences (
    hospital_id   CHAR(36) NOT NULL,
    year          INT      NOT NULL,
    counter_value BIGINT   NOT NULL,
    CONSTRAINT pk_admission_sequences PRIMARY KEY (hospital_id, year),
    CONSTRAINT fk_admission_sequences_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
-- rollback DROP TABLE admission_sequences;
