package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> lockById(long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> readLockById(long id);

    List<Department> findAllByOrderByNameAsc();

    List<Department> findByActiveTrueOrderByNameAsc();

    Optional<Department> findByIdAndActiveTrue(long id);
}
