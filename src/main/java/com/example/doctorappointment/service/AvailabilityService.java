package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.request.SchedulingRequests.Window;
import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses;
import com.example.doctorappointment.entity.Appointment;
import com.example.doctorappointment.entity.DoctorAvailability;
import com.example.doctorappointment.enums.AppointmentStatus;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.AppointmentRepository;
import com.example.doctorappointment.repository.DoctorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {
    private final DoctorService doctors;
    private final DoctorAvailabilityRepository windows;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public PageResponse<SchedulingResponses.Window> list(long userId, Pageable pageable) {
        return PageResponse.from(windows.findByDoctorUserIdAndActiveTrue(userId, pageable).map(AvailabilityService::response));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchedulingResponses.Window create(long userId, Window request) {
        var doctor = doctors.lockOwned(userId);
        validate(request, doctor.getId(), null);
        DoctorAvailability window = new DoctorAvailability();
        window.setDoctor(doctor);
        apply(window, request);
        return response(windows.save(window));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchedulingResponses.Window update(long userId, long id, Window request) {
        var doctor = doctors.lockOwned(userId);
        DoctorAvailability window = owned(id, doctor.getId());
        validate(request, doctor.getId(), id);
        boolean invalidatesBooking = futureBookings(id).stream().anyMatch(a ->
                !a.getAppointmentDate().equals(request.date())
                        || !fits(a.getStartTime(), request.startTime(), request.endTime(), request.slotDurationMinutes())
                        || !a.getEndTime().equals(a.getStartTime().plusMinutes(request.slotDurationMinutes())));
        if (invalidatesBooking) {
            throw bookedWindow();
        }
        apply(window, request);
        return response(window);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deactivate(long userId, long id) {
        var doctor = doctors.lockOwned(userId);
        DoctorAvailability window = owned(id, doctor.getId());
        if (!futureBookings(id).isEmpty()) {
            throw bookedWindow();
        }
        window.setActive(false);
    }

    public List<SchedulingResponses.Slot> slots(long doctorId, LocalDate date) {
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw ApiException.badRequest("INVALID_APPOINTMENT_DATE", "Enter a date in the supported calendar range.");
        }
        var doctor = doctors.find(doctorId);
        doctors.requireBookable(doctor);
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate())) {
            return List.of();
        }
        var booked = appointments.findByDoctorIdAndAppointmentDateAndStatus(
                doctorId, date, AppointmentStatus.CONFIRMED);
        var slots = new ArrayList<SchedulingResponses.Slot>();
        for (var window : windows.findByDoctorIdAndAvailableDateAndActiveTrueOrderByStartTime(doctorId, date)) {
            LocalTime start = window.getStartTime();
            while (fits(start, window.getStartTime(), window.getEndTime(), window.getSlotMinutes())) {
                LocalTime end = start.plusMinutes(window.getSlotMinutes());
                if (date.atTime(start).isAfter(now) && isFree(booked, start, end)) {
                    slots.add(new SchedulingResponses.Slot(start, end));
                }
                start = end;
            }
        }
        return slots;
    }

    DoctorAvailability reserve(long doctorId, LocalDate date, LocalTime start, Long excludedAppointment) {
        if (date.getYear() > 9999 || !minutePrecision(start) || !date.atTime(start).isAfter(LocalDateTime.now(clock))) {
            throw ApiException.badRequest("INVALID_APPOINTMENT_TIME", "Choose a future time at minute precision.");
        }
        var window = windows.lockCoveringWindow(doctorId, date, start)
                .orElseThrow(AvailabilityService::slotUnavailable);
        if (!fits(start, window.getStartTime(), window.getEndTime(), window.getSlotMinutes())) {
            throw ApiException.badRequest("INVALID_SLOT", "The start time does not match the doctor's slot schedule.");
        }
        LocalTime end = start.plusMinutes(window.getSlotMinutes());
        if (appointments.overlaps(doctorId, date, start, end, AppointmentStatus.CONFIRMED, excludedAppointment)) {
            throw slotUnavailable();
        }
        return window;
    }

    private DoctorAvailability owned(long id, long doctorId) {
        return windows.findByIdAndDoctorId(id, doctorId)
                .orElseThrow(() -> ApiException.notFound("Availability window"));
    }

    private void validate(Window request, long doctorId, Long excludedId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.date().getYear() > 9999 || !request.date().atTime(request.endTime()).isAfter(now)) {
            throw ApiException.badRequest("INVALID_AVAILABILITY", "The window must end in the future.");
        }
        long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (!minutePrecision(request.startTime()) || !minutePrecision(request.endTime())
                || minutes <= 0 || minutes % request.slotDurationMinutes() != 0) {
            throw ApiException.badRequest("INVALID_AVAILABILITY",
                    "The window must contain whole slots at minute precision on one calendar date.");
        }
        if (windows.overlaps(doctorId, request.date(), request.startTime(), request.endTime(), excludedId)) {
            throw ApiException.conflict("AVAILABILITY_OVERLAP", "The window overlaps existing availability.");
        }
    }

    private List<Appointment> futureBookings(long windowId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return appointments.findByAvailabilityIdAndStatus(windowId, AppointmentStatus.CONFIRMED).stream()
                .filter(a -> a.getAppointmentDate().atTime(a.getEndTime()).isAfter(now)).toList();
    }

    private static boolean fits(LocalTime time, LocalTime first, LocalTime limit, int minutes) {
        long offset = Duration.between(first, time).toMinutes();
        return offset >= 0 && offset % minutes == 0 && Duration.between(time, limit).toMinutes() >= minutes;
    }

    private static boolean isFree(List<Appointment> booked, LocalTime start, LocalTime end) {
        return booked.stream().noneMatch(a -> a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start));
    }

    private static boolean minutePrecision(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    private void apply(DoctorAvailability window, Window request) {
        window.setAvailableDate(request.date());
        window.setStartTime(request.startTime());
        window.setEndTime(request.endTime());
        window.setSlotMinutes(request.slotDurationMinutes());
        window.setActive(true);
    }

    private static SchedulingResponses.Window response(DoctorAvailability window) {
        return new SchedulingResponses.Window(window.getId(), window.getAvailableDate(),
                window.getStartTime(), window.getEndTime(), window.getSlotMinutes(), window.isActive());
    }

    private static ApiException bookedWindow() {
        return ApiException.conflict("AVAILABILITY_HAS_BOOKINGS", "The change would invalidate a confirmed appointment.");
    }

    private static ApiException slotUnavailable() {
        return ApiException.conflict("APPOINTMENT_SLOT_UNAVAILABLE",
                "The selected appointment slot is no longer available.");
    }
}
