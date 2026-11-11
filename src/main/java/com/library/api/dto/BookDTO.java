package com.library.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Book details. The server generates the ID; omitted availability defaults to true.")
public record BookDTO(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "Generated book ID", example = "6", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @NotBlank @Size(max = 32)
        @Schema(description = "Unique ISBN", example = "9780134685991") String isbn,
        @NotBlank @Size(max = 255)
        @Schema(description = "Book title", example = "Effective Java") String title,
        @NotBlank @Size(max = 255)
        @Schema(description = "Author name", example = "Joshua Bloch") String author,
        @NotBlank @Size(max = 100)
        @Schema(description = "Book category", example = "Programming") String genre,
        @NotNull @Positive
        @Schema(description = "Positive publication year", example = "2018") Integer publishedYear,
        @Schema(description = "Whether the book can be borrowed; defaults to true", example = "true",
                defaultValue = "true") Boolean available) {
}
