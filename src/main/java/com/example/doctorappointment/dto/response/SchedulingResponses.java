package com.example.doctorappointment.dto.response;

import com.example.doctorappointment.enums.AppointmentStatus;
import com.example.doctorappointment.enums.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class SchedulingResponses {
    private SchedulingResponses() {}

    public record Window(long id, LocalDate date, LocalTime startTime, LocalTime endTime,
                         int slotDurationMinutes, boolean active) {}

    public record Slot(LocalTime startTime, LocalTime endTime) {}

    public record Appointment(long id, long doctorId, String doctorName, long patientId,
                              String patientName, LocalDate appointmentDate, LocalTime startTime,
                              LocalTime endTime, String reason, AppointmentStatus status,
                              Instant createdAt, Instant updatedAt, Instant cancelledAt,
                              String cancellationReason, Instant completedAt, List<AppointmentStatus> allowedStatuses) {}

    public record Notification(long id, long appointmentId, NotificationType type, String message,
                               Instant createdAt, Instant readAt) {}

    public record Dashboard(long totalDepartments, long totalDoctors, long totalPatients, long totalAppointments,
                            long appointmentsToday, Map<AppointmentStatus, Long> appointmentsByStatus) {}
}
