--liquibase formatted sql

-- changeset ngolahealth:portal-001-link-users-to-patients
-- comment: Contas do portal: utilizadores com o perfil PATIENT ligados à ficha do doente. Reutilizam
-- comment: as sessões, bloqueios e política de senhas do pessoal; o token leva audiência própria.
ALTER TABLE users ADD COLUMN patient_id CHAR(36) NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES patients (id);
ALTER TABLE users ADD CONSTRAINT uq_users_patient UNIQUE (patient_id);
-- rollback ALTER TABLE users DROP FOREIGN KEY fk_users_patient;
-- rollback ALTER TABLE users DROP INDEX uq_users_patient;
-- rollback ALTER TABLE users DROP COLUMN patient_id;

-- changeset ngolahealth:portal-002-create-activation-codes
-- comment: Códigos de activação entregues em mão na recepção (identidade verificada em pessoa).
-- comment: Guardados só como hash; uso único; expiram; inválidos ao fim de 5 tentativas erradas.
CREATE TABLE portal_activation_codes (
    id              CHAR(36)    NOT NULL,
    hospital_id     CHAR(36)    NOT NULL,
    patient_id      CHAR(36)    NOT NULL,
    code_hash       CHAR(64)    NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    failed_attempts INT         NOT NULL DEFAULT 0,
    used_at         DATETIME(6) NULL,
    revoked_at      DATETIME(6) NULL,
    created_by      CHAR(36)    NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_portal_activation_codes PRIMARY KEY (id),
    CONSTRAINT fk_portal_codes_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_portal_codes_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT uq_portal_codes_hash UNIQUE (code_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_portal_codes_patient ON portal_activation_codes (patient_id, created_at);
-- rollback DROP TABLE portal_activation_codes;
