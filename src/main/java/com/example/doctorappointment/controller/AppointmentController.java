package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.SchedulingRequests.*;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses.Appointment;
import com.example.doctorappointment.enums.AppointmentStatus;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    static final Sort APPOINTMENT_ORDER = Sort.by("appointmentDate", "startTime", "id").descending();

    private final AppointmentService appointments;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public Appointment book(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Booking request) {
        return appointments.book(actor.userId(), request);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<Appointment> patientAppointments(@AuthenticationPrincipal AccountPrincipal actor,
                                                        @Valid @ParameterObject @ModelAttribute AppointmentFilter filter,
                                                        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return appointments.list(actor, filter, PageRequest.of(page, size, APPOINTMENT_ORDER));
    }

    @GetMapping("/doctor/my")
    @PreAuthorize("hasRole('DOCTOR')")
    public PageResponse<Appointment> doctorAppointments(@AuthenticationPrincipal AccountPrincipal actor,
                                                       @Valid @ParameterObject @ModelAttribute AppointmentFilter filter,
                                                       @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return appointments.list(actor, filter, PageRequest.of(page, size, APPOINTMENT_ORDER));
    }

    @GetMapping("/{appointmentId}")
    public Appointment get(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long appointmentId) {
        return appointments.get(actor, appointmentId);
    }

    @PatchMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public Appointment cancel(@AuthenticationPrincipal AccountPrincipal actor,
                              @PathVariable @Positive long appointmentId, @Valid @RequestBody(required = false) Cancel request) {
        return appointments.changeStatus(actor, appointmentId, AppointmentStatus.CANCELLED,
                request == null ? null : request.cancellationReason());
    }

    @PatchMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public Appointment reschedule(@AuthenticationPrincipal AccountPrincipal actor,
                                  @PathVariable @Positive long appointmentId, @Valid @RequestBody Reschedule request) {
        return appointments.reschedule(actor, appointmentId, request);
    }

    @PatchMapping("/{appointmentId}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public Appointment complete(@AuthenticationPrincipal AccountPrincipal actor,
                                @PathVariable @Positive long appointmentId) {
        return appointments.changeStatus(actor, appointmentId, AppointmentStatus.COMPLETED, null);
    }

    @PatchMapping("/{appointmentId}/no-show")
    @PreAuthorize("hasRole('DOCTOR')")
    public Appointment noShow(@AuthenticationPrincipal AccountPrincipal actor,
                              @PathVariable @Positive long appointmentId) {
        return appointments.changeStatus(actor, appointmentId, AppointmentStatus.NO_SHOW, null);
    }
}
