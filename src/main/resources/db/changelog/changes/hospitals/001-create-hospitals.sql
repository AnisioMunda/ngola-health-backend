--liquibase formatted sql

-- changeset ngolahealth:hospitals-001-create-table
-- comment: Instituições de saúde (hospitais, clínicas, centros de saúde...). Raiz do isolamento multi-hospital.
CREATE TABLE hospitals (
    id           CHAR(36)     NOT NULL,
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(20)  NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    province     VARCHAR(100) NOT NULL,
    municipality VARCHAR(100) NULL,
    address      VARCHAR(500) NULL,
    phone        VARCHAR(20)  NULL,
    email        VARCHAR(200) NULL,
    tax_id       VARCHAR(50)  NULL COMMENT 'NIF, usado na facturação electrónica (AGT)',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_hospitals PRIMARY KEY (id),
    CONSTRAINT uq_hospitals_code UNIQUE (code),
    CONSTRAINT ck_hospitals_type CHECK (type IN ('HOSPITAL', 'CLINIC', 'POLYCLINIC', 'HEALTH_CENTER', 'LABORATORY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_hospitals_active_name ON hospitals (active, name);
-- rollback DROP TABLE hospitals;

-- changeset ngolahealth:hospitals-002-seed-default context:dev,test
-- comment: Hospital de demonstração (id fixo, referenciado pelos dados de desenvolvimento dos módulos seguintes).
INSERT INTO hospitals (id, name, code, type, province, municipality, active, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000100', 'Hospital Central de Luanda', 'HCL-001', 'HOSPITAL',
        'Luanda', 'Ingombota', TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
-- rollback DELETE FROM hospitals WHERE id = '00000000-0000-0000-0000-000000000100';
