--liquibase formatted sql

-- changeset ngolahealth:scheduling-001-create-doctor-schedules
-- comment: Horário semanal de consultas de cada médico (dia ISO 1=segunda … 7=domingo).
CREATE TABLE doctor_schedules (
    id               CHAR(36)    NOT NULL,
    hospital_id      CHAR(36)    NOT NULL,
    doctor_id        CHAR(36)    NOT NULL,
    day_of_week      INT         NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    slot_minutes     INT         NOT NULL,
    capacity         INT         NOT NULL COMMENT 'Pacientes por vaga',
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by       CHAR(36)    NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT pk_doctor_schedules PRIMARY KEY (id),
    CONSTRAINT fk_doctor_schedules_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_doctor_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES users (id),
    CONSTRAINT ck_doctor_schedules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_doctor_schedules_times CHECK (start_time < end_time),
    CONSTRAINT ck_doctor_schedules_slot CHECK (slot_minutes BETWEEN 5 AND 240),
    CONSTRAINT ck_doctor_schedules_capacity CHECK (capacity BETWEEN 1 AND 20)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_doctor_schedules_doctor_day ON doctor_schedules (doctor_id, day_of_week, active);
-- rollback DROP TABLE doctor_schedules;

-- changeset ngolahealth:scheduling-002-create-schedule-blocks
-- comment: Ausências do médico (dia inteiro ou intervalo), que tiram vagas da agenda.
CREATE TABLE schedule_blocks (
    id          CHAR(36)     NOT NULL,
    hospital_id CHAR(36)     NOT NULL,
    doctor_id   CHAR(36)     NOT NULL,
    block_date  DATE         NOT NULL,
    start_time  TIME         NULL COMMENT 'NULL = dia inteiro',
    end_time    TIME         NULL,
    reason      VARCHAR(200) NOT NULL,
    created_by  CHAR(36)     NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_schedule_blocks PRIMARY KEY (id),
    CONSTRAINT fk_schedule_blocks_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_schedule_blocks_doctor FOREIGN KEY (doctor_id) REFERENCES users (id),
    CONSTRAINT ck_schedule_blocks_times CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_schedule_blocks_doctor_date ON schedule_blocks (doctor_id, block_date);
-- rollback DROP TABLE schedule_blocks;

-- changeset ngolahealth:scheduling-003-create-appointments
-- comment: Marcações. Os índices únicos sobre "active_flag" (NULL quando cancelada ou falta) fazem
-- comment: na BD o que no PostgreSQL era um índice parcial: lugar da vaga e paciente/hora únicos.
CREATE TABLE appointments (
    id                  CHAR(36)     NOT NULL,
    hospital_id         CHAR(36)     NOT NULL,
    patient_id          CHAR(36)     NOT NULL,
    doctor_id           CHAR(36)     NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    appointment_date    DATE         NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    slot_position       INT          NOT NULL,
    status              VARCHAR(15)  NOT NULL,
    reason              VARCHAR(300) NOT NULL,
    notes               VARCHAR(1000) NULL,
    booked_by           CHAR(36)     NOT NULL,
    booked_at           DATETIME(6)  NOT NULL,
    confirmed_by        CHAR(36)     NULL,
    confirmed_at        DATETIME(6)  NULL,
    checked_in_by       CHAR(36)     NULL,
    checked_in_at       DATETIME(6)  NULL,
    episode_id          CHAR(36)     NULL COMMENT 'Episódio aberto na chegada do paciente',
    no_show_by          CHAR(36)     NULL,
    no_show_at          DATETIME(6)  NULL,
    cancelled_by        CHAR(36)     NULL,
    cancelled_at        DATETIME(6)  NULL,
    cancellation_reason VARCHAR(300) NULL,
    rescheduled_from_id CHAR(36)     NULL,
    rescheduled_to_id   CHAR(36)     NULL,
    active_flag         TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('CANCELLED', 'NO_SHOW') THEN NULL ELSE 1 END) STORED,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT fk_appointments_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_episode FOREIGN KEY (episode_id) REFERENCES episodes (id),
    CONSTRAINT uq_appointments_slot_position UNIQUE (doctor_id, appointment_date, start_time, slot_position, active_flag),
    CONSTRAINT uq_appointments_patient_time UNIQUE (patient_id, appointment_date, start_time, active_flag),
    CONSTRAINT ck_appointments_type CHECK (type IN ('CONSULTATION', 'FOLLOW_UP', 'EXAM')),
    CONSTRAINT ck_appointments_status CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'CHECKED_IN', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT ck_appointments_times CHECK (start_time < end_time),
    CONSTRAINT ck_appointments_position CHECK (slot_position >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_appointments_hospital_date ON appointments (hospital_id, appointment_date, start_time);
CREATE INDEX idx_appointments_doctor_date ON appointments (doctor_id, appointment_date);
CREATE INDEX idx_appointments_patient ON appointments (patient_id, appointment_date);
-- rollback DROP TABLE appointments;
