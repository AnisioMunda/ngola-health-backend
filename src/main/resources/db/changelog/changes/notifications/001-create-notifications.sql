--liquibase formatted sql

-- changeset ngolahealth:notifications-001-create-notifications
-- comment: Notificações internas. Cada linha tem um único destinatário: o estado "lida" é de cada
-- comment: utilizador (no modelo, as notificações para todo o hospital partilhavam o mesmo "lida").
CREATE TABLE notifications (
    id             CHAR(36)      NOT NULL,
    hospital_id    CHAR(36)      NOT NULL,
    recipient_id   CHAR(36)      NOT NULL,
    type           VARCHAR(40)   NOT NULL,
    priority       VARCHAR(10)   NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    link           VARCHAR(300)  NULL COMMENT 'Rota interna do frontend (ex.: /lab/requests/<id>)',
    reference_type VARCHAR(30)   NULL,
    reference_id   CHAR(36)      NULL,
    dedupe_key     VARCHAR(150)  NULL COMMENT 'Evita repetir a mesma notificação ao mesmo destinatário',
    created_at     DATETIME(6)   NOT NULL,
    read_at        DATETIME(6)   NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_notifications_dedupe UNIQUE (recipient_id, dedupe_key),
    CONSTRAINT ck_notifications_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_notifications_link CHECK (link IS NULL OR link LIKE '/%')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at);
CREATE INDEX idx_notifications_unread ON notifications (recipient_id, read_at);
CREATE INDEX idx_notifications_created ON notifications (created_at);
-- rollback DROP TABLE notifications;
