package com.example.doctorappointment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull ZoneId clinicZone,
        @NotBlank String frontendOrigin,
        @NotBlank String jwtSecret,
        @NotNull @DurationMin(seconds = 1) Duration jwtTtl,
        @NotNull @DurationMin Duration cancellationCutoff,
        @NotNull @DurationMin(seconds = 1) Duration resetTtl,
        @NotBlank String resetUrl,
        @NotBlank String mailFrom,
        String adminEmail,
        String adminPassword) {}
