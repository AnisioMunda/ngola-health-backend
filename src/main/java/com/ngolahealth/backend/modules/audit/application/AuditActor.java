package com.ngolahealth.backend.modules.audit.application;

import java.util.UUID;

/** Autor de um evento, quando não é (ou ainda não é) o utilizador autenticado, ex.: login. */
public record AuditActor(UUID id, String username, String fullName) {}
