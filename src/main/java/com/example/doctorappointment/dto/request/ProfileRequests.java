package com.example.doctorappointment.dto.request;

import com.example.doctorappointment.enums.Gender;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProfileRequests {
    private ProfileRequests() {}

    public record DepartmentDetails(@NotBlank @Size(max = 120) String name,
                                    @Size(max = 1000) String description) {}

    public record Status(@NotNull Boolean active) {}

    public record DoctorCreate(@NotBlank @Size(max = 120) String fullName,
                               @NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(min = 12, max = 72) String password,
                               @NotNull @Positive Long departmentId,
                               @NotBlank @Size(max = 120) String specialization,
                               @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
                               @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
                               @Size(max = 160) String qualification,
                               @Min(0) @Max(100) Integer yearsOfExperience,
                               @Size(max = 1000) String bio) {}

    public record DoctorUpdate(@NotBlank @Size(max = 120) String fullName,
                               @NotNull @Positive Long departmentId,
                               @NotBlank @Size(max = 120) String specialization,
                               @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
                               @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
                               @Size(max = 160) String qualification,
                               @Min(0) @Max(100) Integer yearsOfExperience,
                               @Size(max = 1000) String bio) {}

    public record DoctorSelfUpdate(@NotBlank @Size(max = 120) String fullName,
                                   @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
                                   @Pattern(regexp = "(?s).*\\S.*") @Size(max = 120) String specialization,
                                   @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
                                   @Size(max = 160) String qualification,
                                   @Min(0) @Max(100) Integer yearsOfExperience,
                                   @Size(max = 1000) String bio) {}

    public record PatientUpdate(@NotBlank @Size(max = 120) String fullName,
                                @NotBlank @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
                                LocalDate dateOfBirth,
                                Gender gender,
                                @Size(max = 500) String address) {}
}
