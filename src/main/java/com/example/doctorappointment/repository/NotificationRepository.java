package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(long id, long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(long userId, Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.user.id = :userId and n.readAt is null")
    int markRead(long id, long userId, Instant now);
}
