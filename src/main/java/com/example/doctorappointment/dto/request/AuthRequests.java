package com.example.doctorappointment.dto.request;

import jakarta.validation.constraints.*;
import com.example.doctorappointment.enums.Gender;
import java.time.LocalDate;

public final class AuthRequests {
    private AuthRequests() {}

    public record Register(@NotBlank @Size(max = 120) String fullName,
                           @NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(min = 12, max = 72) String password,
                           @NotBlank @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
                           LocalDate dateOfBirth,
                           Gender gender,
                           @Size(max = 500) String address) {}

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 72) String password) {}

    public record ForgotPassword(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPassword(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                                @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    public record ChangePassword(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotBlank @Size(min = 12, max = 72) String newPassword) {}
}
