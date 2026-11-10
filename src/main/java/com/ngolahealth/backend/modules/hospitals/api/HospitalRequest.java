package com.ngolahealth.backend.modules.hospitals.api;

import com.ngolahealth.backend.modules.hospitals.domain.HospitalType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados para registar ou editar um hospital. Os campos opcionais aceitam null ou texto vazio.
 *
 * @param version versão lida pelo cliente; se indicada na edição, recusa a gravação quando o
 *     registo foi alterado entretanto por outra pessoa
 */
public record HospitalRequest(
    @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 200, message = "O nome não pode exceder 200 caracteres.")
        String name,
    @NotBlank(message = "O código é obrigatório.")
        @Pattern(
            regexp = "^\\s*[A-Za-z0-9-]{3,20}\\s*$",
            message = "O código deve ter 3 a 20 letras, números ou hífenes.")
        String code,
    @NotNull(message = "O tipo de instituição é obrigatório.") HospitalType type,
    @NotBlank(message = "A província é obrigatória.")
        @Size(max = 100, message = "A província não pode exceder 100 caracteres.")
        String province,
    @Size(max = 100, message = "O município não pode exceder 100 caracteres.") String municipality,
    @Size(max = 500, message = "A morada não pode exceder 500 caracteres.") String address,
    @Pattern(
            regexp = "^\\s*$|^\\s*\\+?[0-9 ()-]{6,20}\\s*$",
            message = "Indique um telefone válido (apenas dígitos, espaços, parênteses e hífenes).")
        String phone,
    @Email(message = "Indique um correio electrónico válido.")
        @Size(max = 200, message = "O correio electrónico não pode exceder 200 caracteres.")
        String email,
    @Pattern(
            regexp = "^\\s*$|^\\s*[A-Za-z0-9]{9,14}\\s*$",
            message = "O NIF deve ter 9 a 14 letras ou números.")
        String taxId,
    Long version) {}
