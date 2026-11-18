--liquibase formatted sql

-- changeset ngolahealth:equipment-001-create-equipment
-- comment: Equipamentos do hospital. O estado muda pelo fluxo (avaria, manutenção, abate), não à mão.
CREATE TABLE equipment (
    id                         CHAR(36)      NOT NULL,
    hospital_id                CHAR(36)      NOT NULL,
    code                       VARCHAR(50)   NOT NULL,
    name                       VARCHAR(200)  NOT NULL,
    category                   VARCHAR(20)   NOT NULL,
    criticality                VARCHAR(10)   NOT NULL COMMENT 'HIGH = suporte de vida/diagnóstico crítico',
    brand                      VARCHAR(100)  NULL,
    model                      VARCHAR(100)  NULL,
    serial_number              VARCHAR(100)  NULL,
    ward_id                    CHAR(36)      NULL,
    location                   VARCHAR(200)  NULL,
    status                     VARCHAR(20)   NOT NULL,
    purchase_date              DATE          NULL,
    purchase_price             DECIMAL(14,2) NULL,
    warranty_until             DATE          NULL,
    maintenance_interval_days  INT           NULL,
    next_maintenance_date      DATE          NULL,
    calibration_interval_days  INT           NULL,
    next_calibration_date      DATE          NULL,
    notes                      VARCHAR(1000) NULL,
    retired_at                 DATETIME(6)   NULL,
    retired_by                 CHAR(36)      NULL,
    retirement_reason          VARCHAR(300)  NULL,
    version                    BIGINT        NOT NULL DEFAULT 0,
    created_at                 DATETIME(6)   NOT NULL,
    updated_at                 DATETIME(6)   NOT NULL,
    CONSTRAINT pk_equipment PRIMARY KEY (id),
    CONSTRAINT fk_equipment_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_equipment_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT uq_equipment_code UNIQUE (hospital_id, code),
    CONSTRAINT ck_equipment_category CHECK (category IN ('DIAGNOSTIC', 'THERAPEUTIC', 'SURGICAL',
        'MONITORING', 'LIFE_SUPPORT', 'LABORATORY', 'IMAGING', 'MOBILITY', 'IT', 'OTHER')),
    CONSTRAINT ck_equipment_criticality CHECK (criticality IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_equipment_status CHECK (status IN ('OPERATIONAL', 'OUT_OF_SERVICE',
        'UNDER_MAINTENANCE', 'RETIRED')),
    CONSTRAINT ck_equipment_intervals CHECK ((maintenance_interval_days IS NULL OR maintenance_interval_days BETWEEN 1 AND 3650)
        AND (calibration_interval_days IS NULL OR calibration_interval_days BETWEEN 1 AND 3650)),
    CONSTRAINT ck_equipment_price CHECK (purchase_price IS NULL OR purchase_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_equipment_status ON equipment (hospital_id, status);
CREATE INDEX idx_equipment_due ON equipment (hospital_id, next_maintenance_date);
-- rollback DROP TABLE equipment;

-- changeset ngolahealth:equipment-002-create-faults
-- comment: Avarias reportadas por qualquer profissional; resolvem-se com uma manutenção correctiva.
CREATE TABLE equipment_faults (
    id               CHAR(36)     NOT NULL,
    hospital_id      CHAR(36)     NOT NULL,
    equipment_id     CHAR(36)     NOT NULL,
    description      VARCHAR(1000) NOT NULL,
    out_of_service   BOOLEAN      NOT NULL COMMENT 'O equipamento deixou de poder ser usado',
    status           VARCHAR(15)  NOT NULL,
    reported_by      CHAR(36)     NOT NULL,
    reported_at      DATETIME(6)  NOT NULL,
    resolved_at      DATETIME(6)  NULL,
    resolution       VARCHAR(500) NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_equipment_faults PRIMARY KEY (id),
    CONSTRAINT fk_equipment_faults_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_equipment_faults_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT ck_equipment_faults_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_equipment_faults_equipment ON equipment_faults (equipment_id, reported_at);
CREATE INDEX idx_equipment_faults_open ON equipment_faults (hospital_id, status);
-- rollback DROP TABLE equipment_faults;

-- changeset ngolahealth:equipment-003-create-maintenance
-- comment: Histórico imutável de manutenções, calibrações e inspecções.
CREATE TABLE maintenance_records (
    id             CHAR(36)      NOT NULL,
    hospital_id    CHAR(36)      NOT NULL,
    equipment_id   CHAR(36)      NOT NULL,
    fault_id       CHAR(36)      NULL,
    type           VARCHAR(15)   NOT NULL,
    performed_on   DATE          NOT NULL,
    performed_by   VARCHAR(200)  NOT NULL COMMENT 'Técnico ou empresa que fez o trabalho',
    description    VARCHAR(2000) NOT NULL,
    parts_replaced VARCHAR(500)  NULL,
    cost           DECIMAL(14,2) NULL,
    result         VARCHAR(10)   NOT NULL,
    recorded_by    CHAR(36)      NOT NULL,
    recorded_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_maintenance_records PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_maintenance_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_maintenance_fault FOREIGN KEY (fault_id) REFERENCES equipment_faults (id),
    CONSTRAINT ck_maintenance_type CHECK (type IN ('PREVENTIVE', 'CORRECTIVE', 'CALIBRATION', 'INSPECTION')),
    CONSTRAINT ck_maintenance_result CHECK (result IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_maintenance_cost CHECK (cost IS NULL OR cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_maintenance_equipment ON maintenance_records (equipment_id, performed_on);
-- rollback DROP TABLE maintenance_records;
