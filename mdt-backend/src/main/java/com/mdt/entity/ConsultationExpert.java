package com.mdt.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mdt_consultation_expert",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consultation_expert",
                columnNames = {"consultation_id", "expert_id"}
        ))
public class ConsultationExpert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Column(name = "expert_id", nullable = false)
    private Long expertId;

    @Column(name = "expert_name", length = 50)
    private String expertName;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
