package com.ngolahealth.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

  @Test
  void exactTotalBelowTheCap() {
    var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 37);
    var response = PageResponse.of(page, Function.identity());
    assertThat(response.totalElements()).isEqualTo(37);
    assertThat(response.totalPages()).isEqualTo(19);
    assertThat(response.totalCapped()).isFalse();
  }

  @Test
  void totalAboveTheCapIsReportedAsCapped() {
    // A contagem truncada devolve LIMIT + 1 quando há mais resultados.
    var page = new PageImpl<>(List.of("a"), PageRequest.of(3, 20), CappedCount.LIMIT + 1L);
    var response = PageResponse.of(page, Function.identity());
    assertThat(response.totalElements()).isEqualTo(CappedCount.LIMIT);
    assertThat(response.totalPages()).isEqualTo(50);
    assertThat(response.totalCapped()).isTrue();
  }

  @Test
  void exactlyAtTheCapIsNotCapped() {
    var page = new PageImpl<>(List.of("a"), PageRequest.of(0, 20), CappedCount.LIMIT);
    assertThat(PageResponse.of(page, Function.identity()).totalCapped()).isFalse();
  }
}
