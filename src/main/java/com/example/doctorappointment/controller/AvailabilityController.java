package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.SchedulingRequests.Window;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.AvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class AvailabilityController {
    private final AvailabilityService availability;

    @PostMapping("/me/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingResponses.Window create(@AuthenticationPrincipal AccountPrincipal actor,
                                             @Valid @RequestBody Window request) {
        return availability.create(actor.userId(), request);
    }

    @GetMapping("/me/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    public PageResponse<SchedulingResponses.Window> list(@AuthenticationPrincipal AccountPrincipal actor,
                                                         @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return availability.list(actor.userId(), PageRequest.of(page, size, Sort.by("availableDate", "startTime", "id")));
    }

    @PutMapping("/me/availability/{availabilityId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public SchedulingResponses.Window update(@AuthenticationPrincipal AccountPrincipal actor,
                                             @PathVariable @Positive long availabilityId,
                                             @Valid @RequestBody Window request) {
        return availability.update(actor.userId(), availabilityId, request);
    }

    @DeleteMapping("/me/availability/{availabilityId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long availabilityId) {
        availability.deactivate(actor.userId(), availabilityId);
    }

    @GetMapping("/{doctorId}/slots")
    public List<SchedulingResponses.Slot> slots(@PathVariable @Positive long doctorId,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.slots(doctorId, date);
    }
}
