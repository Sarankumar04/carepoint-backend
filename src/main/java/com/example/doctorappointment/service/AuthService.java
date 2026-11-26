package com.example.doctorappointment.service;

import com.example.doctorappointment.config.AppProperties;
import com.example.doctorappointment.dto.request.AuthRequests.ChangePassword;
import com.example.doctorappointment.dto.request.AuthRequests.Register;
import com.example.doctorappointment.dto.request.AuthRequests.ResetPassword;
import com.example.doctorappointment.dto.response.AccountResponses;
import com.example.doctorappointment.entity.Patient;
import com.example.doctorappointment.entity.PasswordResetToken;
import com.example.doctorappointment.entity.User;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.PatientRepository;
import com.example.doctorappointment.repository.PasswordResetTokenRepository;
import com.example.doctorappointment.repository.UserRepository;
import com.example.doctorappointment.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountService accounts;
    private final UserRepository users;
    private final PatientRepository patients;
    private final PasswordResetTokenRepository tokens;
    private final JwtService jwt;
    private final AppProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional
    public AccountResponses.Profile register(Register request) {
        PatientService.validateDateOfBirth(request.dateOfBirth(), clock);
        User user = accounts.create(request.fullName(), request.email(), request.password(), Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setPhone(request.phone().strip());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setAddress(request.address() == null ? null : request.address().strip());
        patients.save(patient);
        return AccountService.profile(user);
    }

    @Transactional(readOnly = true)
    public AccountResponses.Login login(String email, String password) {
        User user = accounts.authenticate(email, password);
        return new AccountResponses.Login(jwt.issue(user), "Bearer", properties.jwtTtl().toSeconds(),
                AccountService.profile(user));
    }

    @Transactional(readOnly = true)
    public AccountResponses.Profile me(long userId) {
        return users.findById(userId).map(AccountService::profile)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forgotPassword(String email) {
        users.findIdByEmail(AccountService.normalizeEmail(email))
                .flatMap(users::lockById)
                .filter(User::isActive)
                .ifPresent(this::issueReset);
    }

    private void issueReset(User user) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant now = clock.instant();

        PasswordResetToken token = tokens.findByUserId(user.getId()).orElseGet(PasswordResetToken::new);
        token.setUser(user);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(properties.resetTtl()));
        token.setUsedAt(null);
        token.setTokenHash(hash(rawToken));
        tokens.save(token);
        events.publishEvent(new PasswordResetMail(user.getEmail(), rawToken));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resetPassword(ResetPassword request) {
        String hash = hash(request.token());
        long userId = tokens.findUserIdByTokenHash(hash).orElseThrow(AuthService::invalidReset);
        User user = accounts.lock(userId);
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByUserId(userId)
                .filter(t -> t.getTokenHash().equals(hash) && t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .filter(t -> user.isActive())
                .orElseThrow(AuthService::invalidReset);
        accounts.setPassword(user, request.newPassword());
        token.setUsedAt(now);
    }

    @Transactional
    public void changePassword(long userId, ChangePassword request) {
        User user = accounts.lock(userId);
        if (!user.isActive() || !accounts.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.invalidCredentials();
        }
        accounts.setPassword(user, request.newPassword());
        tokens.findByUserId(userId).filter(token -> token.getUsedAt() == null)
                .ifPresent(token -> token.setUsedAt(clock.instant()));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static ApiException invalidReset() {
        return ApiException.badRequest("INVALID_RESET_TOKEN", "The password reset token is invalid or expired.");
    }

    record PasswordResetMail(String email, String token) {}
}
