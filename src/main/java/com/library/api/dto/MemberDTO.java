package com.library.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Member details. Omitted membership date uses the server's local date; active defaults to true.")
public record MemberDTO(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "Generated member ID", example = "4", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @NotBlank @Size(max = 255)
        @Schema(description = "Member name", example = "Samir Saleh") String name,
        @NotBlank @Email @Size(max = 255)
        @Schema(description = "Unique email address", example = "samir@example.com") String email,
        @Schema(description = "Membership start date; defaults to the server's local date", example = "2026-11-11")
        LocalDate membershipDate,
        @Schema(description = "Membership active flag; defaults to true", example = "true", defaultValue = "true")
        Boolean active) {
}
