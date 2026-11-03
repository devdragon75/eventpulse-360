package com.eventpulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotNull Long ticketTypeId,
        @NotBlank @Size(max = 100) String attendeeName,
        @NotBlank @Email @Size(max = 150) String attendeeEmail) {
}
