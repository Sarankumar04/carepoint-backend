package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.response.SchedulingResponses.Dashboard;
import com.example.doctorappointment.enums.AppointmentStatus;
import com.example.doctorappointment.repository.AppointmentRepository;
import com.example.doctorappointment.repository.DepartmentRepository;
import com.example.doctorappointment.repository.DoctorRepository;
import com.example.doctorappointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final AppointmentRepository appointments;
    private final DepartmentRepository departments;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Dashboard summary() {
        var totals = new EnumMap<AppointmentStatus, Long>(AppointmentStatus.class);
        for (var status : AppointmentStatus.values()) {
            totals.put(status, 0L);
        }
        appointments.countGroupedByStatus().forEach(count -> totals.put(count.getStatus(), count.getTotal()));
        long totalAppointments = totals.values().stream().mapToLong(Long::longValue).sum();
        return new Dashboard(departments.count(), doctors.count(), patients.count(), totalAppointments,
                appointments.countByAppointmentDate(LocalDate.now(clock)), totals);
    }
}
