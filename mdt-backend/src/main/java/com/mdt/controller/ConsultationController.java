package com.mdt.controller;

import com.mdt.dto.ConsultationDetailResponse;
import com.mdt.dto.CreateConsultationRequest;
import com.mdt.entity.Department;
import com.mdt.entity.Expert;
import com.mdt.service.ConsultationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consultation")
@CrossOrigin(origins = "*")
public class ConsultationController {

    @Resource
    private ConsultationService consultationService;

    @PostMapping("/create")
    public ResponseEntity<ConsultationDetailResponse> createConsultation(
            @Valid @RequestBody CreateConsultationRequest request) {
        ConsultationDetailResponse response = consultationService.createConsultation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ConsultationDetailResponse> acceptConsultation(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long expertId = body.get("expertId");
        ConsultationDetailResponse response = consultationService.acceptConsultation(id, expertId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ConsultationDetailResponse> declineConsultation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long expertId = Long.valueOf(body.get("expertId").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : "";
        ConsultationDetailResponse response = consultationService.declineConsultation(id, expertId, reason);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ConsultationDetailResponse> startConsultation(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        ConsultationDetailResponse response = consultationService.startConsultation(id, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ConsultationDetailResponse> endConsultation(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        ConsultationDetailResponse response = consultationService.endConsultation(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConsultationDetailResponse> getConsultationDetail(@PathVariable Long id) {
        ConsultationDetailResponse response = consultationService.getConsultationDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my/initiated")
    public ResponseEntity<List<ConsultationDetailResponse>> getMyInitiatedConsultations(
            @RequestParam Long userId) {
        List<ConsultationDetailResponse> list = consultationService.getMyInitiatedConsultations(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/my/invitations")
    public ResponseEntity<List<ConsultationDetailResponse>> getMyInvitations(
            @RequestParam Long expertId) {
        List<ConsultationDetailResponse> list = consultationService.getMyInvitations(expertId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getAllDepartments() {
        List<Department> list = consultationService.getAllDepartments();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/experts")
    public ResponseEntity<List<Expert>> getExperts(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) List<Long> departmentIds) {
        List<Expert> experts;
        if (departmentIds != null && !departmentIds.isEmpty()) {
            experts = consultationService.getExpertsByDepartments(departmentIds);
        } else if (departmentId != null) {
            experts = consultationService.getExpertsByDepartment(departmentId);
        } else {
            experts = consultationService.getExpertsByDepartments(List.of());
        }
        return ResponseEntity.ok(experts);
    }
}
