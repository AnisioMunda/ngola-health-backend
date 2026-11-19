package com.ngolahealth.backend.common.web;

/**
 * Contagem truncada para as listas que crescem sem limite (auditoria, episódios, laboratório,
 * receitas). Um {@code COUNT(*)} exacto lê todas as linhas do hospital (centenas de milhares ao fim
 * de alguns anos) só para mostrar "Página 1 de N"; a contagem truncada pára em {@link #LIMIT}+1.
 * Acima do limite o ecrã mostra "mais de 1 000" e quem procura algo concreto usa os filtros.
 *
 * <p>Porquê 1 000: medido com 300 mil episódios, o MySQL lê o índice já ordenado até ~1 000 linhas;
 * a partir de ~2 000 prefere ordenar o hospital inteiro (0,5 s em vez de 2 ms).
 *
 * <p>Uso numa {@code countQuery}: {@code "SELECT COUNT(*) FROM (SELECT x.id AS id FROM … ORDER BY
 * x.data DESC, x.id DESC" + CappedCount.SUBQUERY_LIMIT + ") c"}. O Hibernate exige o {@code ORDER
 * BY}; deve ser o de um índice, para a leitura parar no limite em vez de ordenar tudo. {@link
 * PageResponse#of} assinala o total truncado.
 */
public final class CappedCount {

  public static final int LIMIT = 1_000;

  /** Limite da subconsulta de contagem: um a mais do que {@link #LIMIT} para saber se passou. */
  public static final String SUBQUERY_LIMIT = " LIMIT 1001";

  private CappedCount() {}
}
