package com.example.doctorappointment.service;

import com.example.doctorappointment.config.AppProperties;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AppProperties properties;
    private final UserRepository users;
    private final AccountService accounts;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.adminEmail();
        String password = properties.adminPassword();
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank() || email.length() > 254
                || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalStateException("Set a valid ADMIN_EMAIL and an ADMIN_PASSWORD together.");
        }
        users.findByEmail(AccountService.normalizeEmail(email)).ifPresentOrElse(
                user -> {
                    if (user.getRole() != Role.ADMIN) {
                        throw new IllegalStateException("ADMIN_EMAIL is already assigned to a non-admin account.");
                    }
                },
                () -> accounts.create("Clinic Administrator", email, password, Role.ADMIN));
    }
}
