package com.example.doctorappointment.service;

import com.example.doctorappointment.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {
    private final JavaMailSender mail;
    private final AppProperties properties;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener
    public void send(AuthService.PasswordResetMail event) {
        String resetLink = UriComponentsBuilder.fromUriString(properties.resetUrl())
                .queryParam("token", event.token()).build().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(event.email());
        message.setSubject("Reset your clinic account password");
        message.setText("Use this one-time link to reset your password:\n" + resetLink
                + "\n\nIf you did not request a reset, you can ignore this email.");
        try {
            mail.send(message);
        } catch (MailException ex) {
            log.warn("Password reset email delivery failed ({})", ex.getClass().getSimpleName());
        }
    }
}
