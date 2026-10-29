package dev.frenkli.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 255) String ownerName,
        @NotBlank String currency) {
}
