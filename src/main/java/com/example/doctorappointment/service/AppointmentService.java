package com.example.doctorappointment.service;

import com.example.doctorappointment.config.AppProperties;
import com.example.doctorappointment.dto.request.SchedulingRequests.AppointmentFilter;
import com.example.doctorappointment.dto.request.SchedulingRequests.Booking;
import com.example.doctorappointment.dto.request.SchedulingRequests.Reschedule;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses;
import com.example.doctorappointment.entity.Appointment;
import com.example.doctorappointment.entity.DoctorAvailability;
import com.example.doctorappointment.enums.AppointmentStatus;
import com.example.doctorappointment.enums.NotificationType;
import com.example.doctorappointment.enums.Role;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.AppointmentRepository;
import com.example.doctorappointment.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final NotificationService notifications;
    private final DoctorService doctors;
    private final PatientService patients;
    private final AccountService accounts;
    private final AvailabilityService availability;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchedulingResponses.Appointment book(long userId, Booking request) {
        var doctor = doctors.lock(request.doctorId());
        doctors.requireBookableForWrite(doctor);
        var window = availability.reserve(doctor.getId(), request.appointmentDate(), request.startTime(), null);
        requireActivePatient(userId);
        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patients.owned(userId));
        appointment.setReason(request.reason().strip());
        assignSlot(appointment, window, request.appointmentDate(), request.startTime());
        appointments.saveAndFlush(appointment);
        notifications.appointmentEvent(appointment, NotificationType.BOOKED);
        return response(appointment, Role.PATIENT, clock.instant());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchedulingResponses.Appointment reschedule(AccountPrincipal actor, long id, Reschedule request) {
        long doctorId = appointments.findOwnedDoctorId(id, actor.userId())
                .orElseThrow(() -> ApiException.notFound("Appointment"));
        var doctor = doctors.lock(doctorId);
        Appointment appointment = visible(lock(id), actor);
        requireConfirmed(appointment);
        requireBeforeCutoff(appointment, clock.instant());
        doctors.requireBookableForWrite(doctor);
        var window = availability.reserve(doctorId, request.appointmentDate(), request.startTime(), id);
        requireActivePatient(actor.userId());
        if (appointment.getAppointmentDate().equals(request.appointmentDate())
                && appointment.getStartTime().equals(request.startTime())) {
            return response(appointment, actor.role(), clock.instant());
        }
        assignSlot(appointment, window, request.appointmentDate(), request.startTime());
        appointments.flush();
        notifications.appointmentEvent(appointment, NotificationType.RESCHEDULED);
        return response(appointment, actor.role(), clock.instant());
    }

    public SchedulingResponses.Appointment get(AccountPrincipal actor, long id) {
        return response(visible(appointments.findById(id)
                .orElseThrow(() -> ApiException.notFound("Appointment")), actor), actor.role(), clock.instant());
    }

    public PageResponse<SchedulingResponses.Appointment> list(AccountPrincipal actor, AppointmentFilter filter,
                                                              Pageable pageable) {
        if ((filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate()))
                || !validFilterDate(filter.date()) || !validFilterDate(filter.fromDate())
                || !validFilterDate(filter.toDate())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "Enter a valid date range.");
        }
        Long doctorUser = actor.role() == Role.DOCTOR ? actor.userId() : null;
        Long patientUser = actor.role() == Role.PATIENT ? actor.userId() : null;
        Instant now = clock.instant();
        return PageResponse.from(appointments.search(filter.doctorId(), filter.patientId(), doctorUser, patientUser,
                filter.status(), filter.date(), filter.fromDate(), filter.toDate(), pageable)
                .map(appointment -> response(appointment, actor.role(), now)));
    }

    @Transactional
    public SchedulingResponses.Appointment changeStatus(AccountPrincipal actor, long id, AppointmentStatus status,
                                                        String cancellationReason) {
        var appointment = visible(lock(id), actor);
        requireConfirmed(appointment);
        Instant now = clock.instant();
        NotificationType event = switch (status) {
            case CANCELLED -> {
                requireBeforeCutoff(appointment, now);
                appointment.setCancelledAt(now);
                appointment.setCancellationReason(cancellationReason == null || cancellationReason.isBlank()
                        ? "Cancelled by " + (actor.role() == Role.ADMIN ? "the clinic." : "the patient.")
                        : cancellationReason.strip());
                yield NotificationType.CANCELLED;
            }
            case COMPLETED -> {
                requireStarted(appointment, now);
                appointment.setCompletedAt(now);
                yield NotificationType.COMPLETED;
            }
            case NO_SHOW -> {
                requireStarted(appointment, now);
                yield NotificationType.NO_SHOW;
            }
            case CONFIRMED -> throw invalidTransition();
        };
        appointment.setStatus(status);
        appointments.flush();
        notifications.appointmentEvent(appointment, event);
        return response(appointment, actor.role(), clock.instant());
    }

    private void requireStarted(Appointment appointment, Instant now) {
        if (!hasStarted(appointment, now)) {
            throw ApiException.conflict("APPOINTMENT_NOT_STARTED", "The appointment has not started.");
        }
    }

    private void requireBeforeCutoff(Appointment appointment, Instant now) {
        if (!beforeCutoff(appointment, now)) {
            throw ApiException.conflict("CANCELLATION_CUTOFF_PASSED", "The appointment change cutoff has passed.");
        }
    }

    private boolean hasStarted(Appointment appointment, Instant now) {
        return !now.isBefore(startInstant(appointment));
    }

    private boolean beforeCutoff(Appointment appointment, Instant now) {
        return now.isBefore(startInstant(appointment).minus(properties.cancellationCutoff()));
    }

    private List<AppointmentStatus> allowedStatuses(Appointment appointment, Role role, Instant now) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return List.of();
        }
        if (role != Role.DOCTOR && beforeCutoff(appointment, now)) {
            return List.of(AppointmentStatus.CANCELLED);
        }
        if (role != Role.PATIENT && hasStarted(appointment, now)) {
            return List.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);
        }
        return List.of();
    }

    private Instant startInstant(Appointment appointment) {
        return appointment.getAppointmentDate().atTime(appointment.getStartTime()).atZone(clock.getZone()).toInstant();
    }

    private Appointment lock(long id) {
        return appointments.lockById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    private Appointment visible(Appointment appointment, AccountPrincipal actor) {
        boolean owned = switch (actor.role()) {
            case ADMIN -> true;
            case DOCTOR -> appointment.getDoctor().getUser().getId() == actor.userId();
            case PATIENT -> appointment.getPatient().getUser().getId() == actor.userId();
        };
        if (!owned) {
            throw ApiException.notFound("Appointment");
        }
        return appointment;
    }

    private void requireActivePatient(long userId) {
        if (!accounts.lock(userId).isActive()) {
            throw ApiException.notFound("Active patient");
        }
    }

    private static void requireConfirmed(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw invalidTransition();
        }
    }

    private static ApiException invalidTransition() {
        return ApiException.conflict("INVALID_APPOINTMENT_TRANSITION", "This appointment status change is not allowed.");
    }

    private static boolean validFilterDate(LocalDate date) {
        return date == null || (date.getYear() >= 1000 && date.getYear() <= 9999);
    }

    private static void assignSlot(Appointment appointment, DoctorAvailability window, LocalDate date, LocalTime start) {
        appointment.setAvailability(window);
        appointment.setAppointmentDate(date);
        appointment.setStartTime(start);
        appointment.setEndTime(start.plusMinutes(window.getSlotMinutes()));
    }

    private SchedulingResponses.Appointment response(Appointment a, Role role, Instant now) {
        return new SchedulingResponses.Appointment(a.getId(), a.getDoctor().getId(),
                a.getDoctor().getUser().getFullName(), a.getPatient().getId(),
                a.getPatient().getUser().getFullName(), a.getAppointmentDate(),
                a.getStartTime(), a.getEndTime(), a.getReason(), a.getStatus(), a.getCreatedAt(),
                a.getUpdatedAt(), a.getCancelledAt(), a.getCancellationReason(), a.getCompletedAt(), allowedStatuses(a, role, now));
    }
}
