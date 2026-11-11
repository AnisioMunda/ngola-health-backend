package com.ngolahealth.backend.modules.patients.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientRequest;
import com.ngolahealth.backend.modules.patients.domain.Gender;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientServiceTest {

  @Test
  void normalizeCleansIdentifiersAndOptionalFields() {
    var details =
        PatientService.normalize(
            new PatientRequest(
                "  Ana   Maria  Silva ",
                LocalDate.of(1990, 5, 1),
                Gender.FEMALE,
                " 004468681la042 ",
                "  ",
                " 923 000 000 ",
                " Ana@Mail.AO ",
                null,
                "Luanda",
                "",
                null,
                null,
                null,
                " o+ ",
                "Penicilina",
                null,
                null,
                null));

    assertThat(details.fullName()).isEqualTo("Ana Maria Silva");
    assertThat(details.nationalId()).isEqualTo("004468681LA042");
    assertThat(details.healthCardNumber()).isNull();
    assertThat(details.phone()).isEqualTo("923 000 000");
    assertThat(details.email()).isEqualTo("ana@mail.ao");
    assertThat(details.municipality()).isNull();
    assertThat(details.bloodType()).isEqualTo("O+");
  }

  @Test
  void namePatternUsesFirstAndLastNames() {
    assertThat(PatientService.namePattern("Ana Maria  Silva")).isEqualTo("%Ana%Silva%");
    assertThat(PatientService.namePattern("Ana")).isEqualTo("%Ana%");
    assertThat(PatientService.namePattern("50% Silva")).isEqualTo("%50\\%%Silva%");
  }

  @Test
  void likePatternEscapesWildcards() {
    assertThat(PatientService.likePattern(" ")).isNull();
    assertThat(PatientService.likePattern("a_b")).isEqualTo("%a\\_b%");
  }
}
