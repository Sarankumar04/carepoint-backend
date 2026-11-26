package com.example.doctorappointment.dto.response;

import com.example.doctorappointment.enums.Gender;
import com.example.doctorappointment.enums.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.math.BigDecimal;

public final class AccountResponses {
    private AccountResponses() {}

    public record Profile(long id, String fullName, String email, Role role) {}

    public record Login(String accessToken, String tokenType, long expiresIn, Profile user) {}

    public record Message(String message) {}

    public record Department(long id, String name, String description, boolean active) {}

    public record Doctor(long id, String fullName, Department department, String specialization,
                         BigDecimal consultationFee, String bio, String qualification,
                         Integer yearsOfExperience, boolean active,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String email,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String phone) {}

    public record Patient(long id, String fullName, String email, String phone,
                          LocalDate dateOfBirth, Gender gender, String address, boolean active) {}
}
