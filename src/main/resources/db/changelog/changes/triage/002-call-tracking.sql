--liquibase formatted sql

-- changeset ngolahealth:triage-003-call-tracking
-- comment: Ecrã de chamadas: quantas vezes a senha foi chamada e quando foi a última chamada.
ALTER TABLE triage_records
    ADD COLUMN call_count     INT         NOT NULL DEFAULT 0 COMMENT 'Chamadas da senha (1 = primeira; >1 = chamada de novo)',
    ADD COLUMN last_called_at DATETIME(6) NULL COMMENT 'Última chamada (a primeira fica em called_at)';
UPDATE triage_records SET call_count = 1, last_called_at = called_at WHERE called_at IS NOT NULL;
CREATE INDEX idx_triage_last_called ON triage_records (hospital_id, queue_date, last_called_at);
-- rollback DROP INDEX idx_triage_last_called ON triage_records;
-- rollback ALTER TABLE triage_records DROP COLUMN last_called_at, DROP COLUMN call_count;
