--liquibase formatted sql

-- changeset ngolahealth:users-001-create-users
-- comment: Utilizadores da plataforma. hospital_id é NULL apenas para SUPER_ADMIN (regra garantida no serviço).
CREATE TABLE users (
    id                       CHAR(36)     NOT NULL,
    hospital_id              CHAR(36)     NULL,
    full_name                VARCHAR(200) NOT NULL,
    username                 VARCHAR(50)  NOT NULL,
    email                    VARCHAR(200) NOT NULL,
    password_hash            VARCHAR(100) NOT NULL,
    phone                    VARCHAR(20)  NULL,
    specialty                VARCHAR(100) NULL,
    professional_card_number VARCHAR(50)  NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password     BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts    INT          NOT NULL DEFAULT 0,
    locked_until             DATETIME(6)  NULL,
    last_login_at            DATETIME(6)  NULL,
    password_changed_at      DATETIME(6)  NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_users_hospital_name ON users (hospital_id, full_name);
-- rollback DROP TABLE users;

-- changeset ngolahealth:users-002-create-user-roles
-- comment: Papéis de cada utilizador (enum Role no código; sem tabela de papéis porque o conjunto é fixo).
CREATE TABLE user_roles (
    user_id CHAR(36)    NOT NULL,
    role    VARCHAR(30) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'DOCTOR', 'NURSE',
        'RECEPTIONIST', 'PHARMACIST', 'FINANCIAL', 'LAB_TECHNICIAN', 'PATIENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_user_roles_role ON user_roles (role);
-- rollback DROP TABLE user_roles;

-- changeset ngolahealth:users-003-create-user-sessions
-- comment: Sessões de login. Guarda só o hash SHA-256 do refresh token; o anterior é mantido para detectar reutilização.
CREATE TABLE user_sessions (
    id                  CHAR(36)     NOT NULL,
    user_id             CHAR(36)     NOT NULL,
    token_hash          CHAR(64)     NOT NULL,
    previous_token_hash CHAR(64)     NULL,
    rotated_at          DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    expires_at          DATETIME(6)  NOT NULL,
    revoked_at          DATETIME(6)  NULL,
    revoked_reason      VARCHAR(30)  NULL,
    ip_address          VARCHAR(45)  NULL,
    user_agent          VARCHAR(255) NULL,
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_expires ON user_sessions (expires_at);
-- rollback DROP TABLE user_sessions;
