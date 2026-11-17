--liquibase formatted sql

-- changeset ngolahealth:hr-001-create-shifts
-- comment: Turnos (escala). Início e fim são instantes: um turno da noite atravessa a meia-noite.
CREATE TABLE shifts (
    id          CHAR(36)     NOT NULL,
    hospital_id CHAR(36)     NOT NULL,
    user_id     CHAR(36)     NOT NULL,
    type        VARCHAR(15)  NOT NULL,
    starts_at   DATETIME(6)  NOT NULL,
    ends_at     DATETIME(6)  NOT NULL,
    ward_id     CHAR(36)     NULL,
    department  VARCHAR(100) NULL,
    status      VARCHAR(15)  NOT NULL,
    notes       VARCHAR(300) NULL,
    created_by  CHAR(36)     NOT NULL,
    cancelled_by        CHAR(36)     NULL,
    cancelled_at        DATETIME(6)  NULL,
    cancellation_reason VARCHAR(300) NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_shifts PRIMARY KEY (id),
    CONSTRAINT fk_shifts_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_shifts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_shifts_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT ck_shifts_type CHECK (type IN ('MORNING', 'AFTERNOON', 'NIGHT', 'FULL_DAY', 'ON_CALL')),
    CONSTRAINT ck_shifts_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_shifts_period CHECK (starts_at < ends_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_shifts_user ON shifts (user_id, starts_at);
CREATE INDEX idx_shifts_hospital ON shifts (hospital_id, starts_at);
-- rollback DROP TABLE shifts;

-- changeset ngolahealth:hr-002-create-leave-requests
-- comment: Pedidos de folga/férias/baixa, aprovados por um gestor (nunca pelo próprio).
CREATE TABLE leave_requests (
    id            CHAR(36)     NOT NULL,
    hospital_id   CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    type          VARCHAR(15)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    days          INT          NOT NULL COMMENT 'Dias de calendário, inclusive',
    reason        VARCHAR(500) NULL,
    status        VARCHAR(15)  NOT NULL,
    decided_by    CHAR(36)     NULL,
    decided_at    DATETIME(6)  NULL,
    decision_note VARCHAR(300) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT fk_leave_requests_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_leave_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_leave_requests_type CHECK (type IN ('VACATION', 'SICK', 'PERSONAL', 'MATERNITY',
        'PATERNITY', 'BEREAVEMENT', 'UNPAID', 'COMPENSATORY', 'TRAINING')),
    CONSTRAINT ck_leave_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_leave_requests_period CHECK (start_date <= end_date AND days >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_leave_requests_user ON leave_requests (user_id, start_date);
CREATE INDEX idx_leave_requests_status ON leave_requests (hospital_id, status, start_date);
-- rollback DROP TABLE leave_requests;

-- changeset ngolahealth:hr-003-create-attendance
-- comment: Ponto. Um único registo em aberto (sem saída) por pessoa, garantido pela coluna gerada.
CREATE TABLE attendance_records (
    id                CHAR(36)     NOT NULL,
    hospital_id       CHAR(36)     NOT NULL,
    user_id           CHAR(36)     NOT NULL,
    shift_id          CHAR(36)     NULL,
    clock_in          DATETIME(6)  NOT NULL,
    clock_out         DATETIME(6)  NULL,
    minutes_worked    INT          NULL,
    late_minutes      INT          NOT NULL DEFAULT 0,
    overtime_minutes  INT          NOT NULL DEFAULT 0,
    corrected_by      CHAR(36)     NULL,
    corrected_at      DATETIME(6)  NULL,
    correction_reason VARCHAR(300) NULL,
    open_user_id      CHAR(36) GENERATED ALWAYS AS (CASE WHEN clock_out IS NULL THEN user_id END) STORED,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_attendance_records PRIMARY KEY (id),
    CONSTRAINT fk_attendance_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_attendance_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT uq_attendance_open UNIQUE (open_user_id),
    CONSTRAINT ck_attendance_period CHECK (clock_out IS NULL OR clock_in < clock_out)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_attendance_user ON attendance_records (user_id, clock_in);
CREATE INDEX idx_attendance_hospital ON attendance_records (hospital_id, clock_in);
-- rollback DROP TABLE attendance_records;
