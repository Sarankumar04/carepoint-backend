package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.request.ProfileRequests.PatientUpdate;
import com.example.doctorappointment.dto.response.AccountResponses;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.entity.Patient;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {
    private final PatientRepository patients;
    private final AccountService accounts;
    private final Clock clock;

    public PageResponse<AccountResponses.Patient> list(Boolean active, Pageable pageable) {
        return PageResponse.from(patients.search(active, pageable).map(PatientService::response));
    }

    public AccountResponses.Patient get(long id) {
        return response(patients.findById(id).orElseThrow(() -> ApiException.notFound("Patient")));
    }

    public AccountResponses.Patient me(long userId) {
        return response(owned(userId));
    }

    @Transactional
    public AccountResponses.Patient updateMe(long userId, PatientUpdate request) {
        validateDateOfBirth(request.dateOfBirth(), clock);
        accounts.lock(userId).setFullName(request.fullName().strip());
        Patient patient = owned(userId);
        patient.setPhone(request.phone().strip());
        patient.setDateOfBirth(request.dateOfBirth());
        if (request.gender() != null) {
            patient.setGender(request.gender());
        }
        patient.setAddress(request.address() == null ? null : request.address().strip());
        return response(patient);
    }

    @Transactional
    public AccountResponses.Patient setActive(long id, boolean active) {
        Patient patient = patients.findById(id).orElseThrow(() -> ApiException.notFound("Patient"));
        accounts.setActive(accounts.lock(patient.getUser().getId()), active);
        return response(patient);
    }

    Patient owned(long userId) {
        return patients.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Patient"));
    }

    static void validateDateOfBirth(LocalDate date, Clock clock) {
        if (date != null && (date.isAfter(LocalDate.now(clock)) || date.getYear() < 1000)) {
            throw ApiException.badRequest("INVALID_DATE_OF_BIRTH", "Enter a valid date of birth.");
        }
    }

    private static AccountResponses.Patient response(Patient patient) {
        var user = patient.getUser();
        return new AccountResponses.Patient(patient.getId(), user.getFullName(), user.getEmail(),
                patient.getPhone(), patient.getDateOfBirth(), patient.getGender(), patient.getAddress(), user.isActive());
    }
}
