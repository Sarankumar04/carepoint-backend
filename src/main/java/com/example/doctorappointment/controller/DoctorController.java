package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.ProfileRequests.*;
import com.example.doctorappointment.dto.response.AccountResponses.Doctor;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.DoctorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {
    private final DoctorService doctors;

    @GetMapping
    @SecurityRequirements
    public PageResponse<Doctor> list(@AuthenticationPrincipal AccountPrincipal actor,
                                    @RequestParam(required = false) @Positive Long departmentId,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(required = false) @Size(max = 120) String specialization,
                                    @RequestParam(required = false) @Size(max = 120) String search,
                                    @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return doctors.list(actor != null && actor.role() == Role.ADMIN, departmentId, active, specialization, search,
                PageRequest.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{doctorId}")
    @SecurityRequirements
    public Doctor get(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long doctorId) {
        return doctors.get(doctorId, actor != null && actor.role() == Role.ADMIN);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Doctor create(@Valid @RequestBody DoctorCreate request) {
        return doctors.create(request);
    }

    @PutMapping("/{doctorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Doctor update(@PathVariable @Positive long doctorId, @Valid @RequestBody DoctorUpdate request) {
        return doctors.update(doctorId, request);
    }

    @PatchMapping("/{doctorId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Doctor setActive(@PathVariable @Positive long doctorId, @Valid @RequestBody Status request) {
        return doctors.setActive(doctorId, request.active());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public Doctor me(@AuthenticationPrincipal AccountPrincipal actor) {
        return doctors.me(actor.userId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public Doctor updateMe(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody DoctorSelfUpdate request) {
        return doctors.updateMe(actor.userId(), request);
    }
}
