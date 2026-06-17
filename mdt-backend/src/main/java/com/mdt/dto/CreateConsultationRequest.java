package com.mdt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateConsultationRequest {

    @NotBlank(message = "会诊标题不能为空")
    private String title;

    private String patientName;

    private String patientInfo;

    private String description;

    private Long initiatorId;

    private String initiatorName;

    @NotEmpty(message = "请至少选择一位专家")
    private List<Long> expertIds;
}
