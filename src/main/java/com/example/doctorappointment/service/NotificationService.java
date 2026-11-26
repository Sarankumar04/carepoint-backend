package com.example.doctorappointment.service;

import com.example.doctorappointment.dto.response.PageResponse;
import com.example.doctorappointment.dto.response.SchedulingResponses;
import com.example.doctorappointment.entity.Appointment;
import com.example.doctorappointment.entity.Notification;
import com.example.doctorappointment.entity.User;
import com.example.doctorappointment.enums.NotificationType;
import com.example.doctorappointment.exception.ApiException;
import com.example.doctorappointment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<SchedulingResponses.Notification> list(long userId, Pageable pageable) {
        return PageResponse.from(notifications.findByUserId(userId, pageable).map(NotificationService::response));
    }

    @Transactional
    public void read(long userId, long id) {
        if (notifications.markRead(id, userId, clock.instant()) == 0) {
            notifications.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Notification"));
        }
    }

    @Transactional
    public void readAll(long userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    void appointmentEvent(Appointment appointment, NotificationType type) {
        notify(appointment.getPatient().getUser(), appointment, type);
        notify(appointment.getDoctor().getUser(), appointment, type);
    }

    private void notify(User recipient, Appointment appointment, NotificationType type) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setAppointment(appointment);
        notification.setType(type);
        notification.setMessage(switch (type) {
            case BOOKED -> "An appointment was booked.";
            case RESCHEDULED -> "An appointment was rescheduled.";
            case CANCELLED -> "An appointment was cancelled.";
            case COMPLETED -> "An appointment was completed.";
            case NO_SHOW -> "An appointment was marked as a no-show.";
        });
        notifications.save(notification);
    }

    private static SchedulingResponses.Notification response(Notification notification) {
        return new SchedulingResponses.Notification(notification.getId(), notification.getAppointment().getId(),
                notification.getType(), notification.getMessage(), notification.getCreatedAt(), notification.getReadAt());
    }
}
