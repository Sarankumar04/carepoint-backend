package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.AuthRequests.*;
import com.example.doctorappointment.dto.response.AccountResponses;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/register")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponses.Profile register(@Valid @RequestBody Register request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    public AccountResponses.Login login(@Valid @RequestBody Login request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public AccountResponses.Profile me(@AuthenticationPrincipal AccountPrincipal actor) {
        return auth.me(actor.userId());
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Request an email with a one-time reset link")
    public AccountResponses.Message forgotPassword(@Valid @RequestBody ForgotPassword request) {
        auth.forgotPassword(request.email());
        return new AccountResponses.Message("If an active account exists, a reset link will be emailed.");
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPassword request) {
        auth.resetPassword(request);
    }

    @PatchMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AccountPrincipal actor,
                               @Valid @RequestBody ChangePassword request) {
        auth.changePassword(actor.userId(), request);
    }
}
