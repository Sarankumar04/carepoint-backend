package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.response.AccountResponses.Profile;
import com.example.doctorappointment.entity.User;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    User create(String fullName, String email, String password, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw duplicateEmail();
        }

        User user = new User();
        user.setFullName(fullName.strip());
        user.setEmail(normalizedEmail);
        user.setRole(role);
        setPassword(user, password);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof java.sql.SQLException sql && sql.getErrorCode() == 1062) {
                throw duplicateEmail();
            }
            throw ex;
        }
    }

    User authenticate(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email)).orElse(null);
        // Verify an equivalent BCrypt hash for unknown accounts.
        String hash = user == null
                ? "$2a$12$w54k91VvXeM/x4GYvB3Y0OtU.rDAWqTuTtUtkTzLrHuTYgKQlgHi2"
                : user.getPasswordHash();
        if (!matches(password, hash) || user == null || !user.isActive()) {
            throw ApiException.invalidCredentials();
        }
        return user;
    }

    boolean matches(String password, String hash) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(password, hash);
    }

    void setPassword(User user, String password) {
        if (password.length() < 12 || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Use at least 12 characters and at most 72 UTF-8 bytes.");
        }
        user.setPasswordHash(passwords.encode(password));
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    void setActive(User user, boolean active) {
        if (user.isActive() != active) {
            user.setActive(active);
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
    }

    User lock(long userId) {
        return users.lockById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    static Profile profile(User user) {
        return new Profile(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private ApiException duplicateEmail() {
        return ApiException.conflict("DUPLICATE_EMAIL", "An account already uses this email address.");
    }
}
