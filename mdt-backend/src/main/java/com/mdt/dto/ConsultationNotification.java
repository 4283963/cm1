package com.mdt.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultationNotification {
    private String type;
    private Long consultationId;
    private String consultationNo;
    private String title;
    private String initiatorName;
    private String patientName;
    private String roomId;
    private LocalDateTime createdAt;
    private String message;
}
