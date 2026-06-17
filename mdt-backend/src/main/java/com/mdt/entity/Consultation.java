package com.mdt.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mdt_consultation")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_no", nullable = false, unique = true, length = 32)
    private String consultationNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "patient_name", length = 50)
    private String patientName;

    @Column(name = "patient_info", length = 1000)
    private String patientInfo;

    @Column(length = 2000)
    private String description;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Column(name = "initiator_name", length = 50)
    private String initiatorName;

    @Column(name = "room_id", length = 64)
    private String roomId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
