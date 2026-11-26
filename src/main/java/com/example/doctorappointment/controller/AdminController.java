package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.SchedulingRequests.AppointmentFilter;
import com.example.doctorappointment.dto.request.SchedulingRequests.StatusUpdate;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses.Appointment;
import com.example.doctorappointment.dto.response.SchedulingResponses.Dashboard;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.AppointmentService;
import com.example.doctorappointment.service.DashboardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AppointmentService appointments;
    private final DashboardService dashboard;

    @GetMapping("/dashboard/summary")
    public Dashboard summary() {
        return dashboard.summary();
    }

    @GetMapping("/appointments")
    public PageResponse<Appointment> list(@AuthenticationPrincipal AccountPrincipal actor,
                                          @Valid @ParameterObject @ModelAttribute AppointmentFilter filter,
                                          @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return appointments.list(actor, filter, PageRequest.of(page, size, AppointmentController.APPOINTMENT_ORDER));
    }

    @GetMapping("/appointments/{appointmentId}")
    public Appointment get(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long appointmentId) {
        return appointments.get(actor, appointmentId);
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    public Appointment changeStatus(@AuthenticationPrincipal AccountPrincipal actor,
                                     @PathVariable @Positive long appointmentId,
                                     @Valid @RequestBody StatusUpdate request) {
        return appointments.changeStatus(actor, appointmentId, request.status(), request.cancellationReason());
    }
}
