package com.example.doctorappointment.dto.request;

import jakarta.validation.constraints.*;
import com.example.doctorappointment.enums.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public final class SchedulingRequests {
    private SchedulingRequests() {}

    public record Window(@NotNull LocalDate date,
                         @NotNull LocalTime startTime,
                         @NotNull LocalTime endTime,
                         @JsonAlias("slotMinutes") @NotNull @Min(1) @Max(480) Integer slotDurationMinutes) {}

    public record Booking(@NotNull @Positive Long doctorId,
                          @NotNull LocalDate appointmentDate,
                          @NotNull LocalTime startTime,
                          @NotBlank @Size(max = 1000) String reason) {}

    public record Reschedule(@NotNull LocalDate appointmentDate, @NotNull LocalTime startTime) {}

    public record Cancel(@Size(max = 500) String cancellationReason) {}

    public record StatusUpdate(@NotNull AppointmentStatus status,
                               @Size(max = 500) String cancellationReason) {}

    public record AppointmentFilter(@Positive Long doctorId, @Positive Long patientId,
                                    AppointmentStatus status,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {}
}
