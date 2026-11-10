package com.ngolahealth.backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Garante o contrato problem+json (status, title, detail e code) para cada tipo de erro. */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void notFoundExceptionMapsTo404WithCode() throws Exception {
    mockMvc
        .perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Paciente não encontrado."));
  }

  @Test
  void conflictExceptionMapsTo409() throws Exception {
    mockMvc
        .perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE"));
  }

  @Test
  void validationErrorsReturnFieldMessages() throws Exception {
    mockMvc
        .perform(
            post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.name").value("O nome é obrigatório."));
  }

  @Test
  void malformedJsonReturns400() throws Exception {
    mockMvc
        .perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void dataIntegrityViolationReturns409WithoutLeakingSql() throws Exception {
    mockMvc
        .perform(get("/integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
        .andExpect(
            jsonPath("$.detail")
                .value("Os dados informados entram em conflito com um registo existente."));
  }

  @Test
  void unexpectedErrorReturns500WithoutDetails() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(
            jsonPath("$.detail").value("Ocorreu um erro interno. Tente novamente mais tarde."));
  }

  record NameRequest(@NotBlank(message = "O nome é obrigatório.") String name) {}

  @RestController
  static class TestController {

    @GetMapping("/not-found")
    void notFound() {
      throw new NotFoundException("PATIENT_NOT_FOUND", "Paciente não encontrado.");
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new ConflictException("DUPLICATE", "Registo duplicado.");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody NameRequest request) {}

    @GetMapping("/integrity")
    void integrity() {
      throw new DataIntegrityViolationException("Duplicate entry 'x' for key 'uq_secret_column'");
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("segredo interno");
    }
  }
}
