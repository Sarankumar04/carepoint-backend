package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses.Notification;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.NotificationService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    public PageResponse<Notification> list(@AuthenticationPrincipal AccountPrincipal actor,
                                           @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.list(actor.userId(), PageRequest.of(page, size, Sort.by("createdAt", "id").descending()));
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long notificationId) {
        notifications.read(actor.userId(), notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal AccountPrincipal actor) {
        notifications.readAll(actor.userId());
    }
}
