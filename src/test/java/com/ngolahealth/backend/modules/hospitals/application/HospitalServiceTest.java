package com.ngolahealth.backend.modules.hospitals.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngolahealth.backend.common.exception.BusinessException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.hospitals.api.HospitalRequest;
import com.ngolahealth.backend.modules.hospitals.domain.Hospital;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalRepository;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HospitalServiceTest {

  @Mock private HospitalRepository repository;
  @InjectMocks private HospitalService service;

  @Test
  void normalizeTrimsUppercasesCodeAndTurnsBlankOptionalsIntoNull() {
    var details =
        HospitalService.normalize(
            new HospitalRequest(
                "  Hospital Geral  ",
                " hgl-001 ",
                HospitalType.CLINIC,
                " Luanda ",
                "   ",
                "",
                null,
                " Geral@Hospital.AO ",
                " 5417000000la ",
                null));

    assertThat(details.name()).isEqualTo("Hospital Geral");
    assertThat(details.code()).isEqualTo("HGL-001");
    assertThat(details.province()).isEqualTo("Luanda");
    assertThat(details.municipality()).isNull();
    assertThat(details.address()).isNull();
    assertThat(details.phone()).isNull();
    assertThat(details.email()).isEqualTo("geral@hospital.ao");
    assertThat(details.taxId()).isEqualTo("5417000000LA");
  }

  @Test
  void likePatternEscapesWildcardsAndIgnoresBlankSearch() {
    assertThat(HospitalService.likePattern(null)).isNull();
    assertThat(HospitalService.likePattern("   ")).isNull();
    assertThat(HospitalService.likePattern(" Luanda ")).isEqualTo("%luanda%");
    assertThat(HospitalService.likePattern("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
  }

  @Test
  void createRejectsDuplicateCodeWithConflict() {
    when(repository.existsByCode("HCL-001")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request("hcl-001", null)))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((BusinessException) e).getCode())
        .isEqualTo(HospitalService.CODE_IN_USE);
    verify(repository, never()).save(any());
  }

  @Test
  void updateRejectsCodeUsedByAnotherHospital() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(hospital()));
    when(repository.existsByCodeAndIdNot("HCL-002", id)).thenReturn(true);

    assertThatThrownBy(() -> service.update(id, request("hcl-002", null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("HCL-002");
  }

  @Test
  void updateRejectsStaleVersion() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(hospital()));

    assertThatThrownBy(() -> service.update(id, request("HCL-002", 5L)))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((BusinessException) e).getCode())
        .isEqualTo(HospitalService.CONCURRENT_MODIFICATION);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void unknownHospitalIsNotFound() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(id))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Hospital não encontrado.");
  }

  @Test
  void setActiveToCurrentStateDoesNotWrite() {
    var id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(hospital()));

    var response = service.setActive(id, true);

    assertThat(response.active()).isTrue();
    verify(repository, never()).saveAndFlush(any());
  }

  private static Hospital hospital() {
    return new Hospital(HospitalService.normalize(request("HCL-001", null)));
  }

  private static HospitalRequest request(String code, Long version) {
    return new HospitalRequest(
        "Hospital de Teste",
        code,
        HospitalType.HOSPITAL,
        "Luanda",
        null,
        null,
        null,
        null,
        null,
        version);
  }
}
