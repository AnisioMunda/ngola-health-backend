--liquibase formatted sql

-- changeset ngolahealth:audit-001-create-audit-events
-- comment: Registo de auditoria só de inserção (a aplicação nunca altera nem apaga). Os dados do autor são copiados para o evento, para sobreviverem a alterações da conta.
CREATE TABLE audit_events (
    id              CHAR(36)     NOT NULL,
    occurred_at     DATETIME(6)  NOT NULL,
    hospital_id     CHAR(36)     NULL COMMENT 'Hospital a que o evento diz respeito; NULL = nível da plataforma',
    actor_id        CHAR(36)     NULL,
    actor_username  VARCHAR(100) NULL,
    actor_name      VARCHAR(200) NULL,
    action          VARCHAR(30)  NOT NULL,
    entity_type     VARCHAR(30)  NOT NULL,
    entity_id       VARCHAR(64)  NULL,
    summary         VARCHAR(500) NOT NULL,
    changes         JSON         NULL COMMENT 'Criação: dados iniciais; edição: {campo: {from, to}}',
    outcome         VARCHAR(20)  NOT NULL,
    ip_address      VARCHAR(45)  NULL,
    user_agent      VARCHAR(255) NULL,
    request_method  VARCHAR(10)  NULL,
    request_path    VARCHAR(300) NULL,
    request_id      VARCHAR(36)  NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_audit_events_hospital_time ON audit_events (hospital_id, occurred_at);
CREATE INDEX idx_audit_events_time ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_actor_time ON audit_events (actor_id, occurred_at);
-- rollback DROP TABLE audit_events;
