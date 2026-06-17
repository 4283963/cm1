package com.mdt.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConsultationDetailResponse {
    private Long id;
    private String consultationNo;
    private String title;
    private String patientName;
    private String patientInfo;
    private String description;
    private Long initiatorId;
    private String initiatorName;
    private String roomId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private List<ExpertInfo> experts;

    @Data
    public static class ExpertInfo {
        private Long expertId;
        private String expertName;
        private String departmentName;
        private String status;
        private LocalDateTime joinedAt;
    }
}
