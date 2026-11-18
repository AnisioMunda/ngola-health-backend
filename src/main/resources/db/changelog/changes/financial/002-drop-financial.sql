--liquibase formatted sql

-- changeset ngolahealth:financial-002-drop-financial
-- comment: O módulo financeiro foi retirado do sistema (2026-11-18). As migrações anteriores não se
-- comment: alteram (histórico imutável); esta apaga as tabelas. O código continua no histórico do Git.
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS invoice_items;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS document_sequences;
DROP TABLE IF EXISTS service_prices;
-- rollback SELECT 'Para repor o financeiro, recupere o módulo do histórico do Git e crie uma migração nova.';
