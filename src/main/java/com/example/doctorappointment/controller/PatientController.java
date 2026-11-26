package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.ProfileRequests.PatientUpdate;
import com.example.doctorappointment.dto.request.ProfileRequests.Status;
import com.example.doctorappointment.dto.response.AccountResponses.Patient;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.PatientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientService patients;

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public Patient me(@AuthenticationPrincipal AccountPrincipal actor) {
        return patients.me(actor.userId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public Patient updateMe(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody PatientUpdate request) {
        return patients.updateMe(actor.userId(), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<Patient> list(@RequestParam(required = false) Boolean active,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return patients.list(active, PageRequest.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Patient get(@PathVariable @Positive long patientId) {
        return patients.get(patientId);
    }

    @PatchMapping("/{patientId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Patient setActive(@PathVariable @Positive long patientId, @Valid @RequestBody Status request) {
        return patients.setActive(patientId, request.active());
    }
}
