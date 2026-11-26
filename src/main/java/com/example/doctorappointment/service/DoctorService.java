package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.request.ProfileRequests.DoctorCreate;
import com.example.doctorappointment.dto.request.ProfileRequests.DoctorSelfUpdate;
import com.example.doctorappointment.dto.request.ProfileRequests.DoctorUpdate;
import com.example.doctorappointment.dto.response.AccountResponses;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.entity.Department;
import com.example.doctorappointment.entity.Doctor;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoctorService {
    private final DoctorRepository doctors;
    private final DepartmentService departments;
    private final AccountService accounts;

    public PageResponse<AccountResponses.Doctor> list(boolean admin, Long departmentId, Boolean active,
                                                       String specialization, String search, Pageable pageable) {
        return PageResponse.from(doctors.search(admin, departmentId, active, likePattern(specialization),
                likePattern(search), pageable).map(doctor -> response(doctor, admin)));
    }

    public AccountResponses.Doctor get(long id, boolean admin) {
        Doctor doctor = find(id);
        if (!admin) {
            requireBookable(doctor);
        }
        return response(doctor, admin);
    }

    public AccountResponses.Doctor me(long userId) {
        return response(doctors.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Doctor")), true);
    }

    @Transactional
    public AccountResponses.Doctor create(DoctorCreate request) {
        Doctor doctor = new Doctor();
        updateDepartment(doctor, request.departmentId());
        updateProfessionalDetails(doctor, request.specialization(), request.consultationFee(), request.bio(),
                request.phone(), request.qualification(), request.yearsOfExperience());
        doctor.setUser(accounts.create(request.fullName(), request.email(), request.password(), Role.DOCTOR));
        return response(doctors.save(doctor), true);
    }

    @Transactional
    public AccountResponses.Doctor update(long id, DoctorUpdate request) {
        Doctor doctor = lock(id);
        updateDepartment(doctor, request.departmentId());
        updateProfessionalDetails(doctor, request.specialization(), request.consultationFee(), request.bio(),
                request.phone(), request.qualification(), request.yearsOfExperience());
        accounts.lock(doctor.getUser().getId()).setFullName(request.fullName().strip());
        return response(doctor, true);
    }

    @Transactional
    public AccountResponses.Doctor updateMe(long userId, DoctorSelfUpdate request) {
        Doctor doctor = lockOwned(userId);
        accounts.lock(userId).setFullName(request.fullName().strip());
        updateProfessionalDetails(doctor, request.specialization(), request.consultationFee(), request.bio(),
                request.phone(), request.qualification(), request.yearsOfExperience());
        return response(doctor, true);
    }

    @Transactional
    public AccountResponses.Doctor setActive(long id, boolean active) {
        Doctor doctor = lock(id);
        accounts.setActive(accounts.lock(doctor.getUser().getId()), active);
        return response(doctor, true);
    }

    Doctor find(long id) {
        return doctors.findById(id).orElseThrow(() -> ApiException.notFound("Doctor"));
    }

    Doctor lock(long id) {
        return doctors.lockById(id).orElseThrow(() -> ApiException.notFound("Doctor"));
    }

    Doctor lockOwned(long userId) {
        return doctors.lockByUserId(userId).orElseThrow(() -> ApiException.notFound("Doctor"));
    }

    void requireBookable(Doctor doctor) {
        if (!doctor.getUser().isActive() || !doctor.getDepartment().isActive()) {
            throw ApiException.notFound("Active doctor");
        }
    }

    void requireBookableForWrite(Doctor doctor) {
        if (!doctor.getUser().isActive() || !departments.readLock(doctor.getDepartment().getId()).isActive()) {
            throw ApiException.notFound("Active doctor");
        }
    }

    private void updateDepartment(Doctor doctor, long departmentId) {
        Department department = departments.lock(departmentId);
        if (!department.isActive() && (doctor.getDepartment() == null
                || !doctor.getDepartment().getId().equals(department.getId()))) {
            throw ApiException.conflict("DEPARTMENT_INACTIVE", "The department is inactive.");
        }
        doctor.setDepartment(department);
    }

    private void updateProfessionalDetails(Doctor doctor, String specialization, BigDecimal fee, String bio,
                                           String phone, String qualification, Integer experience) {
        if (specialization != null) {
            doctor.setSpecialization(specialization.strip());
        }
        if (fee != null) {
            doctor.setConsultationFee(fee);
        }
        if (phone != null) {
            doctor.setPhone(phone.strip());
        }
        if (qualification != null) {
            doctor.setQualification(qualification.strip());
        }
        if (experience != null) {
            doctor.setYearsOfExperience(experience);
        }
        doctor.setBio(bio);
    }

    private static String likePattern(String text) {
        return text == null || text.isBlank() ? null
                : "%" + text.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static AccountResponses.Doctor response(Doctor doctor, boolean includeContact) {
        var user = doctor.getUser();
        return new AccountResponses.Doctor(doctor.getId(), user.getFullName(),
                DepartmentService.response(doctor.getDepartment()), doctor.getSpecialization(),
                doctor.getConsultationFee(), doctor.getBio(), doctor.getQualification(),
                doctor.getYearsOfExperience(), user.isActive(),
                includeContact ? user.getEmail() : null, includeContact ? doctor.getPhone() : null);
    }
}
