package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.request.ProfileRequests.DepartmentDetails;
import com.example.doctorappointment.dto.response.AccountResponses;
import com.example.doctorappointment.entity.Department;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departments;

    public List<AccountResponses.Department> list(boolean admin) {
        return (admin ? departments.findAllByOrderByNameAsc() : departments.findByActiveTrueOrderByNameAsc())
                .stream().map(DepartmentService::response).toList();
    }

    public AccountResponses.Department get(long id, boolean admin) {
        var department = admin ? departments.findById(id) : departments.findByIdAndActiveTrue(id);
        return department.map(DepartmentService::response).orElseThrow(() -> ApiException.notFound("Department"));
    }

    @Transactional
    public AccountResponses.Department create(DepartmentDetails request) {
        Department department = new Department();
        apply(department, request);
        return response(departments.save(department));
    }

    @Transactional
    public AccountResponses.Department update(long id, DepartmentDetails request) {
        Department department = lock(id);
        apply(department, request);
        return response(department);
    }

    @Transactional
    public AccountResponses.Department setActive(long id, boolean active) {
        Department department = lock(id);
        department.setActive(active);
        return response(department);
    }

    Department lock(long id) {
        return departments.lockById(id).orElseThrow(() -> ApiException.notFound("Department"));
    }

    Department readLock(long id) {
        return departments.readLockById(id).orElseThrow(() -> ApiException.notFound("Department"));
    }

    private void apply(Department department, DepartmentDetails request) {
        department.setName(request.name().strip());
        department.setDescription(request.description());
    }

    static AccountResponses.Department response(Department department) {
        return new AccountResponses.Department(department.getId(), department.getName(),
                department.getDescription(), department.isActive());
    }
}
