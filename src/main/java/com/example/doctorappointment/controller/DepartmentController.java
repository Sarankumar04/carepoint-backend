package com.example.doctorappointment.controller;

import com.example.doctorappointment.dto.request.ProfileRequests.DepartmentDetails;
import com.example.doctorappointment.dto.request.ProfileRequests.Status;
import com.example.doctorappointment.dto.response.AccountResponses.Department;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.security.AccountPrincipal;
import com.example.doctorappointment.service.DepartmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departments;

    @GetMapping
    @SecurityRequirements
    public List<Department> list(@AuthenticationPrincipal AccountPrincipal actor) {
        return departments.list(actor != null && actor.role() == Role.ADMIN);
    }

    @GetMapping("/{departmentId}")
    @SecurityRequirements
    public Department get(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable @Positive long departmentId) {
        return departments.get(departmentId, actor != null && actor.role() == Role.ADMIN);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Department create(@Valid @RequestBody DepartmentDetails request) {
        return departments.create(request);
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Department update(@PathVariable @Positive long departmentId, @Valid @RequestBody DepartmentDetails request) {
        return departments.update(departmentId, request);
    }

    @PatchMapping("/{departmentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Department setActive(@PathVariable @Positive long departmentId, @Valid @RequestBody Status request) {
        return departments.setActive(departmentId, request.active());
    }
}
