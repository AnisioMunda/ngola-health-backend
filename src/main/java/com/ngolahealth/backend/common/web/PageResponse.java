package com.ngolahealth.backend.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados com formato JSON estável (não depende da serialização interna do Spring
 * Data).
 *
 * @param totalCapped o total foi truncado em {@link CappedCount#LIMIT}: há pelo menos esse número
 *     de resultados (o ecrã mostra "mais de …")
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean totalCapped) {

  public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
    long total = page.getTotalElements();
    boolean capped = total > CappedCount.LIMIT;
    long shown = capped ? CappedCount.LIMIT : total;
    int pages = page.getSize() == 0 ? 1 : (int) ((shown + page.getSize() - 1) / page.getSize());
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        shown,
        capped ? pages : page.getTotalPages(),
        capped);
  }
}
