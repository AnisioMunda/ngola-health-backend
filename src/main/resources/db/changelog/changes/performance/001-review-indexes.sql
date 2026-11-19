--liquibase formatted sql

-- Revisão de índices (módulo 20), medida numa base descartável com 50 mil doentes, 300 mil
-- episódios, 200 mil marcações, 150 mil triagens, 100 mil pedidos de laboratório e receitas,
-- 500 mil eventos de auditoria e 300 mil notificações. Os índices existentes começam por
-- (hospital_id, status, …): servem as listas de trabalho filtradas por estado, mas não as
-- pesquisas por período sem estado (dashboard, históricos), que liam todo o hospital.
-- ALGORITHM=INPLACE, LOCK=NONE: criação online, sem bloquear escritas; se o MySQL não o puder
-- garantir, a migração falha em vez de bloquear a tabela.

-- changeset ngolahealth:performance-001-episodes-time
-- comment: Histórico de episódios sem filtro de estado e dashboard (por tipo, estado e dia). O id vem logo a seguir à data para servir a ordenação com desempate (scheduled_at, id) sem ordenar o hospital inteiro; type e status cobrem as agregações sem ler a tabela.
CREATE INDEX idx_episodes_hospital_time ON episodes (hospital_id, scheduled_at, id, type, status) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_episodes_hospital_time ON episodes;

-- changeset ngolahealth:performance-001-triage-time
-- comment: Indicadores de triagem por período (total, por prioridade, tempo de espera). Índice de cobertura.
CREATE INDEX idx_triage_hospital_time ON triage_records (hospital_id, triaged_at, priority, called_at) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_triage_hospital_time ON triage_records;

-- changeset ngolahealth:performance-001-lab-requests-time
-- comment: Histórico do laboratório (mais recentes primeiro) e indicadores por período.
CREATE INDEX idx_lab_requests_hospital_time ON lab_requests (hospital_id, requested_at) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_lab_requests_hospital_time ON lab_requests;

-- changeset ngolahealth:performance-001-prescriptions-time
-- comment: Histórico de receitas (mais recentes primeiro).
CREATE INDEX idx_prescriptions_hospital_time ON prescriptions (hospital_id, issued_at) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_prescriptions_hospital_time ON prescriptions;

-- changeset ngolahealth:performance-001-patients-name
-- comment: Lista de doentes por nome sem filtro de activo (a ordem vem do índice; pára ao fim de uma página).
CREATE INDEX idx_patients_hospital_name ON patients (hospital_id, full_name, record_number) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_patients_hospital_name ON patients;

-- changeset ngolahealth:performance-001-patients-created
-- comment: Dashboard: doentes novos no período.
CREATE INDEX idx_patients_hospital_created ON patients (hospital_id, created_at) ALGORITHM=INPLACE LOCK=NONE;
-- rollback DROP INDEX idx_patients_hospital_created ON patients;
