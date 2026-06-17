package com.mdt.service;

import com.mdt.dto.ConsultationDetailResponse;
import com.mdt.dto.ConsultationNotification;
import com.mdt.dto.CreateConsultationRequest;
import com.mdt.entity.*;
import com.mdt.redis.ParticipantInfo;
import com.mdt.redis.RoomRouteService;
import com.mdt.repository.ConsultationExpertRepository;
import com.mdt.repository.ConsultationRepository;
import com.mdt.repository.DepartmentRepository;
import com.mdt.repository.ExpertRepository;
import com.mdt.websocket.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConsultationService {

    @Resource
    private ConsultationRepository consultationRepository;

    @Resource
    private ConsultationExpertRepository consultationExpertRepository;

    @Resource
    private ExpertRepository expertRepository;

    @Resource
    private DepartmentRepository departmentRepository;

    @Resource
    private RoomRouteService roomRouteService;

    @Resource
    private NotificationService notificationService;

    private static final String ROOM_ID_PREFIX = "MDT-ROOM-";

    @Transactional
    public ConsultationDetailResponse createConsultation(CreateConsultationRequest request) {
        log.info("发起MDT会诊, 标题: {}, 专家数量: {}", request.getTitle(), request.getExpertIds().size());

        String consultationNo = generateConsultationNo();
        String roomId = ROOM_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Consultation consultation = new Consultation();
        consultation.setConsultationNo(consultationNo);
        consultation.setTitle(request.getTitle());
        consultation.setPatientName(request.getPatientName());
        consultation.setPatientInfo(request.getPatientInfo());
        consultation.setDescription(request.getDescription());
        consultation.setInitiatorId(request.getInitiatorId());
        consultation.setInitiatorName(request.getInitiatorName());
        consultation.setRoomId(roomId);
        consultation.setStatus(ConsultationStatus.PENDING);
        consultation = consultationRepository.save(consultation);

        List<Expert> experts = expertRepository.findAllById(request.getExpertIds());
        Map<Long, String> deptNameMap = new HashMap<>();
        for (Expert expert : experts) {
            deptNameMap.computeIfAbsent(expert.getDepartmentId(), k -> {
                return departmentRepository.findById(k).map(Department::getName).orElse("");
            });
        }

        List<ConsultationExpert> consultationExperts = new ArrayList<>();
        for (Expert expert : experts) {
            ConsultationExpert ce = new ConsultationExpert();
            ce.setConsultationId(consultation.getId());
            ce.setExpertId(expert.getId());
            ce.setExpertName(expert.getName());
            ce.setDepartmentName(deptNameMap.getOrDefault(expert.getDepartmentId(), ""));
            ce.setStatus(ConsultationExpertStatus.INVITED);
            consultationExperts.add(ce);
        }
        consultationExpertRepository.saveAll(consultationExperts);

        roomRouteService.createRoom(roomId, consultation.getId(), consultationNo);

        ParticipantInfo initiatorInfo = new ParticipantInfo();
        initiatorInfo.setUserId(request.getInitiatorId());
        initiatorInfo.setUserName(request.getInitiatorName());
        initiatorInfo.setRole("INITIATOR");
        initiatorInfo.setStatus("INVITED");
        roomRouteService.addParticipant(roomId, initiatorInfo);
        roomRouteService.setUserRoute(request.getInitiatorId().toString(), roomId);

        for (Expert expert : experts) {
            ConsultationNotification notification = new ConsultationNotification();
            notification.setType("INVITE");
            notification.setConsultationId(consultation.getId());
            notification.setConsultationNo(consultationNo);
            notification.setTitle(request.getTitle());
            notification.setInitiatorName(request.getInitiatorName());
            notification.setPatientName(request.getPatientName());
            notification.setRoomId(roomId);
            notification.setCreatedAt(consultation.getCreatedAt());
            notification.setMessage("您收到一个新的MDT会诊邀请，请及时处理！");

            notificationService.sendConsultationInvite(expert.getId(), notification);
            log.info("已向专家 {}({}) 发送会诊邀请", expert.getName(), expert.getId());
        }

        log.info("MDT会诊创建成功, 会诊号: {}, 房间ID: {}", consultationNo, roomId);
        return buildDetailResponse(consultation, consultationExperts);
    }

    @Transactional
    public ConsultationDetailResponse acceptConsultation(Long consultationId, Long expertId) {
        log.info("专家 {} 接受会诊邀请: {}", expertId, consultationId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        ConsultationExpert consultationExpert = consultationExpertRepository
                .findByConsultationIdAndExpertId(consultationId, expertId)
                .orElseThrow(() -> new RuntimeException("专家未被邀请"));

        consultationExpert.setStatus(ConsultationExpertStatus.ACCEPTED);
        consultationExpertRepository.save(consultationExpert);

        Expert expert = expertRepository.findById(expertId).orElse(null);
        String departmentName = expert != null && expert.getDepartmentId() != null
                ? departmentRepository.findById(expert.getDepartmentId()).map(Department::getName).orElse("")
                : "";

        ParticipantInfo participantInfo = new ParticipantInfo();
        participantInfo.setUserId(expertId);
        participantInfo.setUserName(consultationExpert.getExpertName());
        participantInfo.setDepartmentName(departmentName);
        participantInfo.setRole("EXPERT");
        participantInfo.setStatus("ACCEPTED");
        roomRouteService.addParticipant(consultation.getRoomId(), participantInfo);
        roomRouteService.setUserRoute(expertId.toString(), consultation.getRoomId());

        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("expertId", expertId);
        statusUpdate.put("expertName", consultationExpert.getExpertName());
        statusUpdate.put("status", ConsultationExpertStatus.ACCEPTED);
        notificationService.sendExpertStatusUpdate(consultationId, statusUpdate);

        List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    @Transactional
    public ConsultationDetailResponse declineConsultation(Long consultationId, Long expertId, String reason) {
        log.info("专家 {} 拒绝会诊邀请: {}, 原因: {}", expertId, consultationId, reason);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        ConsultationExpert consultationExpert = consultationExpertRepository
                .findByConsultationIdAndExpertId(consultationId, expertId)
                .orElseThrow(() -> new RuntimeException("专家未被邀请"));

        consultationExpert.setStatus(ConsultationExpertStatus.DECLINED);
        consultationExpertRepository.save(consultationExpert);

        roomRouteService.removeParticipant(consultation.getRoomId(), expertId);
        roomRouteService.clearUserRoute(expertId.toString());

        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("expertId", expertId);
        statusUpdate.put("expertName", consultationExpert.getExpertName());
        statusUpdate.put("status", ConsultationExpertStatus.DECLINED);
        statusUpdate.put("reason", reason);
        notificationService.sendExpertStatusUpdate(consultationId, statusUpdate);

        List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    @Transactional
    public ConsultationDetailResponse startConsultation(Long consultationId, Long userId) {
        log.info("开始会诊: {}, 发起人: {}", consultationId, userId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (!consultation.getInitiatorId().equals(userId)) {
            throw new RuntimeException("只有发起人可以开始会诊");
        }

        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        consultation.setStartedAt(LocalDateTime.now());
        consultation = consultationRepository.save(consultation);

        roomRouteService.updateRoomStatus(consultation.getRoomId(), "IN_PROGRESS");

        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONSULTATION_STARTED");
        message.put("consultationId", consultationId);
        message.put("roomId", consultation.getRoomId());
        message.put("startedAt", consultation.getStartedAt());
        notificationService.sendRoomBroadcast(consultation.getRoomId(), message);

        List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    @Transactional
    public ConsultationDetailResponse endConsultation(Long consultationId, Long userId) {
        log.info("结束会诊: {}, 操作人: {}", consultationId, userId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (!consultation.getInitiatorId().equals(userId)) {
            throw new RuntimeException("只有发起人可以结束会诊");
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setEndedAt(LocalDateTime.now());
        consultation = consultationRepository.save(consultation);

        List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(consultationId);
        for (ConsultationExpert expert : experts) {
            if (ConsultationExpertStatus.JOINED.equals(expert.getStatus())
                    || ConsultationExpertStatus.ACCEPTED.equals(expert.getStatus())) {
                expert.setStatus(ConsultationExpertStatus.LEFT);
                consultationExpertRepository.save(expert);
                roomRouteService.clearUserRoute(expert.getExpertId().toString());
            }
        }

        roomRouteService.deleteRoom(consultation.getRoomId());
        notificationService.broadcastConsultationEnded(consultationId);

        return buildDetailResponse(consultation, experts);
    }

    public ConsultationDetailResponse getConsultationDetail(Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));
        List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    public List<ConsultationDetailResponse> getMyInitiatedConsultations(Long userId) {
        List<Consultation> consultations = consultationRepository.findByInitiatorIdOrderByCreatedAtDesc(userId);
        return consultations.stream().map(c -> {
            List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(c.getId());
            return buildDetailResponse(c, experts);
        }).collect(Collectors.toList());
    }

    public List<ConsultationDetailResponse> getMyInvitations(Long expertId) {
        List<ConsultationExpert> expertConsultations = consultationExpertRepository.findByExpertId(expertId);
        return expertConsultations.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ce -> {
                    Consultation c = consultationRepository.findById(ce.getConsultationId()).orElse(null);
                    if (c == null) return null;
                    List<ConsultationExpert> experts = consultationExpertRepository.findByConsultationId(c.getId());
                    return buildDetailResponse(c, experts);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Expert> getExpertsByDepartment(Long departmentId) {
        return expertRepository.findByDepartmentId(departmentId);
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public List<Expert> getExpertsByDepartments(List<Long> departmentIds) {
        return expertRepository.findByDepartmentIdIn(departmentIds);
    }

    private String generateConsultationNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "MDT" + dateStr + random;
    }

    private ConsultationDetailResponse buildDetailResponse(Consultation consultation,
                                                           List<ConsultationExpert> experts) {
        ConsultationDetailResponse response = new ConsultationDetailResponse();
        response.setId(consultation.getId());
        response.setConsultationNo(consultation.getConsultationNo());
        response.setTitle(consultation.getTitle());
        response.setPatientName(consultation.getPatientName());
        response.setPatientInfo(consultation.getPatientInfo());
        response.setDescription(consultation.getDescription());
        response.setInitiatorId(consultation.getInitiatorId());
        response.setInitiatorName(consultation.getInitiatorName());
        response.setRoomId(consultation.getRoomId());
        response.setStatus(consultation.getStatus());
        response.setStartedAt(consultation.getStartedAt());
        response.setEndedAt(consultation.getEndedAt());
        response.setCreatedAt(consultation.getCreatedAt());

        List<ConsultationDetailResponse.ExpertInfo> expertInfos = experts.stream().map(ce -> {
            ConsultationDetailResponse.ExpertInfo info = new ConsultationDetailResponse.ExpertInfo();
            info.setExpertId(ce.getExpertId());
            info.setExpertName(ce.getExpertName());
            info.setDepartmentName(ce.getDepartmentName());
            info.setStatus(ce.getStatus());
            info.setJoinedAt(ce.getJoinedAt());
            return info;
        }).collect(Collectors.toList());
        response.setExperts(expertInfos);

        return response;
    }
}
