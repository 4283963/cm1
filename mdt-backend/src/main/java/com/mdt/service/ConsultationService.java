package com.mdt.service;

import com.mdt.dto.ConsultationDetailResponse;
import com.mdt.dto.ConsultationNotification;
import com.mdt.dto.CreateConsultationRequest;
import com.mdt.entity.*;
import com.mdt.redis.DistributedLock;
import com.mdt.redis.ParticipantInfo;
import com.mdt.redis.RoomRouteInfo;
import com.mdt.redis.RoomRouteService;
import com.mdt.repository.ConsultationExpertRepository;
import com.mdt.repository.ConsultationRepository;
import com.mdt.repository.DepartmentRepository;
import com.mdt.repository.ExpertRepository;
import com.mdt.websocket.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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

    @Resource
    private DistributedLock distributedLock;

    private static final String ROOM_ID_PREFIX = "MDT-ROOM-";
    private static final String LOCK_CREATE_CONSULT = "create:%s";
    private static final String LOCK_ACCEPT = "accept:%d:%d";
    private static final String LOCK_DECLINE = "decline:%d:%d";
    private static final String LOCK_START = "start:%d";
    private static final String LOCK_END = "end:%d";
    private static final long LOCK_LEASE_MS = 10_000L;

    private static final Set<String> ACCEPTABLE_FROM_STATUSES = Set.of(
            ConsultationExpertStatus.INVITED
    );

    private static final Set<String> ACCEPTED_TERMINAL_STATUSES = Set.of(
            ConsultationExpertStatus.ACCEPTED,
            ConsultationExpertStatus.JOINED
    );

    private static final Set<String> DECLINABLE_FROM_STATUSES = Set.of(
            ConsultationExpertStatus.INVITED
    );

    public ConsultationDetailResponse createConsultation(CreateConsultationRequest request) {
        String lockKey = String.format(LOCK_CREATE_CONSULT, request.getInitiatorId() + ":" + request.getTitle().hashCode());
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doCreateConsultation(request));
    }

    @Transactional
    protected ConsultationDetailResponse doCreateConsultation(CreateConsultationRequest request) {
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
            deptNameMap.computeIfAbsent(expert.getDepartmentId(), k ->
                    departmentRepository.findById(k).map(Department::getName).orElse("")
            );
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
        try {
            consultationExpertRepository.saveAll(consultationExperts);
        } catch (Exception e) {
            log.warn("批量保存会诊专家时出现约束冲突，开始逐条幂等插入: {}", e.getMessage());
            consultationExperts = saveExpertsIdempotently(consultation.getId(), consultationExperts);
        }

        roomRouteService.createRoom(roomId, consultation.getId(), consultationNo,
                request.getInitiatorId(), request.getInitiatorName());

        ParticipantInfo initiatorInfo = new ParticipantInfo();
        initiatorInfo.setUserId(request.getInitiatorId());
        initiatorInfo.setUserName(request.getInitiatorName());
        initiatorInfo.setRole("INITIATOR");
        initiatorInfo.setStatus("JOINED");
        roomRouteService.addParticipantIfAbsent(roomId, initiatorInfo);
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

    private List<ConsultationExpert> saveExpertsIdempotently(Long consultationId,
                                                              List<ConsultationExpert> experts) {
        List<ConsultationExpert> result = new ArrayList<>();
        for (ConsultationExpert ce : experts) {
            try {
                Optional<ConsultationExpert> existing =
                        consultationExpertRepository.findByConsultationIdAndExpertId(
                                consultationId, ce.getExpertId());
                if (existing.isPresent()) {
                    result.add(existing.get());
                } else {
                    result.add(consultationExpertRepository.save(ce));
                }
            } catch (Exception ex) {
                consultationExpertRepository.findByConsultationIdAndExpertId(
                                consultationId, ce.getExpertId())
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    public ConsultationDetailResponse acceptConsultation(Long consultationId, Long expertId) {
        String lockKey = String.format(LOCK_ACCEPT, consultationId, expertId);
        int retryCount = 0;
        int maxRetries = 3;

        while (true) {
            try {
                return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                        () -> doAcceptConsultation(consultationId, expertId));
            } catch (OptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("接受会诊乐观锁重试次数耗尽, consultationId={}, expertId={}",
                            consultationId, expertId);
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }
                log.warn("接受会诊遇到乐观锁冲突, 第{}次重试, consultationId={}, expertId={}",
                        retryCount, consultationId, expertId);
                try {
                    Thread.sleep(50L * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("操作被中断");
                }
            }
        }
    }

    @Transactional
    protected ConsultationDetailResponse doAcceptConsultation(Long consultationId, Long expertId) {
        log.info("专家 {} 接受会诊邀请: {}", expertId, consultationId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (ConsultationStatus.COMPLETED.equals(consultation.getStatus())
                || ConsultationStatus.CANCELLED.equals(consultation.getStatus())) {
            throw new RuntimeException("会诊已结束，无法加入");
        }

        ConsultationExpert consultationExpert = consultationExpertRepository
                .findByConsultationIdAndExpertId(consultationId, expertId)
                .orElseThrow(() -> new RuntimeException("专家未被邀请"));

        String currentStatus = consultationExpert.getStatus();

        if (ACCEPTED_TERMINAL_STATUSES.contains(currentStatus)) {
            log.info("专家 {} 已接受/加入会诊 {} (当前状态: {}), 幂等直接返回",
                    expertId, consultationId, currentStatus);
            List<ConsultationExpert> experts =
                    consultationExpertRepository.findByConsultationId(consultationId);
            return buildDetailResponse(consultation, experts);
        }

        if (!ACCEPTABLE_FROM_STATUSES.contains(currentStatus)) {
            throw new RuntimeException("当前状态不允许接受邀请，当前状态: " + currentStatus);
        }

        consultationExpert.setStatus(ConsultationExpertStatus.ACCEPTED);
        consultationExpert.setJoinedAt(LocalDateTime.now());
        consultationExpertRepository.save(consultationExpert);

        Expert expert = expertRepository.findById(expertId).orElse(null);
        String departmentName = expert != null && expert.getDepartmentId() != null
                ? departmentRepository.findById(expert.getDepartmentId())
                        .map(Department::getName).orElse("")
                : "";

        ParticipantInfo participantInfo = new ParticipantInfo();
        participantInfo.setUserId(expertId);
        participantInfo.setUserName(consultationExpert.getExpertName());
        participantInfo.setDepartmentName(departmentName);
        participantInfo.setRole("EXPERT");
        participantInfo.setStatus("ACCEPTED");
        roomRouteService.addParticipantIfAbsent(consultation.getRoomId(), participantInfo);
        roomRouteService.setUserRoute(expertId.toString(), consultation.getRoomId());

        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("expertId", expertId);
        statusUpdate.put("expertName", consultationExpert.getExpertName());
        statusUpdate.put("status", ConsultationExpertStatus.ACCEPTED);
        statusUpdate.put("departmentName", departmentName);
        notificationService.sendExpertStatusUpdate(consultationId, statusUpdate);

        log.info("专家 {} 成功接受会诊邀请: {}", expertId, consultationId);
        List<ConsultationExpert> experts =
                consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    public ConsultationDetailResponse declineConsultation(Long consultationId,
                                                          Long expertId,
                                                          String reason) {
        String lockKey = String.format(LOCK_DECLINE, consultationId, expertId);
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doDeclineConsultation(consultationId, expertId, reason));
    }

    @Transactional
    protected ConsultationDetailResponse doDeclineConsultation(Long consultationId,
                                                                Long expertId,
                                                                String reason) {
        log.info("专家 {} 拒绝会诊邀请: {}, 原因: {}", expertId, consultationId, reason);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        ConsultationExpert consultationExpert = consultationExpertRepository
                .findByConsultationIdAndExpertId(consultationId, expertId)
                .orElseThrow(() -> new RuntimeException("专家未被邀请"));

        String currentStatus = consultationExpert.getStatus();

        if (ConsultationExpertStatus.DECLINED.equals(currentStatus)
                || ConsultationExpertStatus.LEFT.equals(currentStatus)) {
            log.info("专家 {} 已拒绝/离开会诊 {} (当前状态: {}), 幂等直接返回",
                    expertId, consultationId, currentStatus);
            List<ConsultationExpert> experts =
                    consultationExpertRepository.findByConsultationId(consultationId);
            return buildDetailResponse(consultation, experts);
        }

        if (!DECLINABLE_FROM_STATUSES.contains(currentStatus)
                && !ConsultationExpertStatus.ACCEPTED.equals(currentStatus)) {
            throw new RuntimeException("当前状态不允许拒绝邀请");
        }

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

        List<ConsultationExpert> experts =
                consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    public ConsultationDetailResponse startConsultation(Long consultationId, Long userId) {
        String lockKey = String.format(LOCK_START, consultationId);
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doStartConsultation(consultationId, userId));
    }

    @Transactional
    protected ConsultationDetailResponse doStartConsultation(Long consultationId, Long userId) {
        log.info("开始会诊: {}, 发起人: {}", consultationId, userId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (!consultation.getInitiatorId().equals(userId)) {
            throw new RuntimeException("只有发起人可以开始会诊");
        }

        if (ConsultationStatus.IN_PROGRESS.equals(consultation.getStatus())) {
            log.info("会诊 {} 已在进行中, 幂等直接返回", consultationId);
            List<ConsultationExpert> experts =
                    consultationExpertRepository.findByConsultationId(consultationId);
            return buildDetailResponse(consultation, experts);
        }

        if (ConsultationStatus.COMPLETED.equals(consultation.getStatus())
                || ConsultationStatus.CANCELLED.equals(consultation.getStatus())) {
            throw new RuntimeException("会诊已结束，无法重新开始");
        }

        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        consultation.setStartedAt(LocalDateTime.now());
        consultation = consultationRepository.save(consultation);

        roomRouteService.updateRoomStatus(consultation.getRoomId(), "IN_PROGRESS");

        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONSULTATION_STARTED");
        message.put("consultationId", consultationId);
        message.put("roomId", consultation.getRoomId());
        message.put("startedAt", consultation.getStartedAt().toString());
        notificationService.sendRoomBroadcast(consultation.getRoomId(), message);

        List<ConsultationExpert> experts =
                consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    public ConsultationDetailResponse endConsultation(Long consultationId, Long userId) {
        String lockKey = String.format(LOCK_END, consultationId);
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doEndConsultation(consultationId, userId));
    }

    @Transactional
    protected ConsultationDetailResponse doEndConsultation(Long consultationId, Long userId) {
        log.info("结束会诊: {}, 操作人: {}", consultationId, userId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (!consultation.getInitiatorId().equals(userId)) {
            throw new RuntimeException("只有发起人可以结束会诊");
        }

        if (ConsultationStatus.COMPLETED.equals(consultation.getStatus())
                || ConsultationStatus.CANCELLED.equals(consultation.getStatus())) {
            log.info("会诊 {} 已结束, 幂等直接返回", consultationId);
            List<ConsultationExpert> experts =
                    consultationExpertRepository.findByConsultationId(consultationId);
            return buildDetailResponse(consultation, experts);
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setEndedAt(LocalDateTime.now());
        consultation = consultationRepository.save(consultation);

        List<ConsultationExpert> experts =
                consultationExpertRepository.findByConsultationId(consultationId);
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

    @Transactional(readOnly = true)
    public ConsultationDetailResponse getConsultationDetail(Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));
        List<ConsultationExpert> experts =
                consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, experts);
    }

    @Transactional(readOnly = true)
    public List<ConsultationDetailResponse> getMyInitiatedConsultations(Long userId) {
        List<Consultation> consultations =
                consultationRepository.findByInitiatorIdOrderByCreatedAtDesc(userId);
        return consultations.stream().map(c -> {
            List<ConsultationExpert> experts =
                    consultationExpertRepository.findByConsultationId(c.getId());
            return buildDetailResponse(c, experts);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConsultationDetailResponse> getMyInvitations(Long expertId) {
        List<ConsultationExpert> expertConsultations =
                consultationExpertRepository.findByExpertId(expertId);
        return expertConsultations.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ce -> {
                    Consultation c =
                            consultationRepository.findById(ce.getConsultationId()).orElse(null);
                    if (c == null) return null;
                    List<ConsultationExpert> experts =
                            consultationExpertRepository.findByConsultationId(c.getId());
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

    private static final String LOCK_INVITE_ADDITIONAL = "invite_additional:%d";
    private static final String LOCK_TRANSFER_PRESENTER = "transfer_presenter:%d";
    private static final String LOCK_CONTROL_EVENT = "control_event:%s";

    public ConsultationDetailResponse inviteAdditionalExperts(Long consultationId,
                                                              Long operatorId,
                                                              List<Long> newExpertIds,
                                                              boolean isUrgent) {
        String lockKey = String.format(LOCK_INVITE_ADDITIONAL, consultationId);
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doInviteAdditionalExperts(consultationId, operatorId, newExpertIds, isUrgent));
    }

    @Transactional
    protected ConsultationDetailResponse doInviteAdditionalExperts(Long consultationId,
                                                                    Long operatorId,
                                                                    List<Long> newExpertIds,
                                                                    boolean isUrgent) {
        log.info("会诊中追加专家邀请: consultationId={}, operatorId={}, newExpertIds={}, urgent={}",
                consultationId, operatorId, newExpertIds, isUrgent);

        if (newExpertIds == null || newExpertIds.isEmpty()) {
            throw new RuntimeException("请至少选择一位要邀请的专家");
        }

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        if (ConsultationStatus.COMPLETED.equals(consultation.getStatus())
                || ConsultationStatus.CANCELLED.equals(consultation.getStatus())) {
            throw new RuntimeException("会诊已结束，无法追加邀请");
        }

        List<ConsultationExpert> existingList =
                consultationExpertRepository.findByConsultationId(consultationId);
        Set<Long> existingExpertIds = existingList.stream()
                .map(ConsultationExpert::getExpertId)
                .collect(Collectors.toSet());

        List<Long> filteredIds = newExpertIds.stream()
                .filter(id -> !existingExpertIds.contains(id))
                .distinct()
                .collect(Collectors.toList());

        if (filteredIds.isEmpty()) {
            log.info("要邀请的专家均已在会诊中，幂等直接返回");
            return buildDetailResponse(consultation, existingList);
        }

        List<Expert> newExperts = expertRepository.findAllById(filteredIds);
        Map<Long, String> deptNameMap = new HashMap<>();
        for (Expert expert : newExperts) {
            deptNameMap.computeIfAbsent(expert.getDepartmentId(), k ->
                    departmentRepository.findById(k).map(Department::getName).orElse("")
            );
        }

        List<ConsultationExpert> newConsultationExperts = new ArrayList<>();
        for (Expert expert : newExperts) {
            ConsultationExpert ce = new ConsultationExpert();
            ce.setConsultationId(consultationId);
            ce.setExpertId(expert.getId());
            ce.setExpertName(expert.getName());
            ce.setDepartmentName(deptNameMap.getOrDefault(expert.getDepartmentId(), ""));
            ce.setStatus(ConsultationExpertStatus.INVITED);
            newConsultationExperts.add(ce);
        }
        consultationExpertRepository.saveAll(newConsultationExperts);

        ParticipantInfo operator = roomRouteService.getParticipants(consultation.getRoomId())
                .get(operatorId);
        String operatorName = operator != null ? operator.getUserName()
                : (consultation.getInitiatorId().equals(operatorId) ? consultation.getInitiatorName() : "专家");

        for (Expert expert : newExperts) {
            ParticipantInfo pi = new ParticipantInfo();
            pi.setUserId(expert.getId());
            pi.setUserName(expert.getName());
            pi.setDepartmentName(deptNameMap.getOrDefault(expert.getDepartmentId(), ""));
            pi.setRole("EXPERT");
            pi.setStatus("INVITED");
            roomRouteService.addParticipantIfAbsent(consultation.getRoomId(), pi);

            ConsultationNotification notification = new ConsultationNotification();
            notification.setType(isUrgent ? "URGENT_INVITE" : "INVITE");
            notification.setConsultationId(consultationId);
            notification.setConsultationNo(consultation.getConsultationNo());
            notification.setTitle(consultation.getTitle());
            notification.setInitiatorName(operatorName);
            notification.setPatientName(consultation.getPatientName());
            notification.setRoomId(consultation.getRoomId());
            notification.setCreatedAt(consultation.getCreatedAt());
            notification.setMessage(isUrgent
                    ? String.format("【紧急呼叫】%s 在会诊中紧急邀请您加入，请立即处理！", operatorName)
                    : String.format("%s 在会诊中追加邀请您加入", operatorName));

            notificationService.sendConsultationInvite(expert.getId(), notification);

            Map<String, Object> roomMsg = new HashMap<>();
            roomMsg.put("type", "EXPERT_INVITED");
            roomMsg.put("urgent", isUrgent);
            roomMsg.put("expertId", expert.getId());
            roomMsg.put("expertName", expert.getName());
            roomMsg.put("departmentName", deptNameMap.getOrDefault(expert.getDepartmentId(), ""));
            roomMsg.put("operatorId", operatorId);
            roomMsg.put("operatorName", operatorName);
            notificationService.sendRoomBroadcast(consultation.getRoomId(), roomMsg);

            log.info("已向专家 {}({}) 发送{}会诊邀请", expert.getName(), expert.getId(),
                    isUrgent ? "紧急" : "追加");
        }

        List<ConsultationExpert> allExperts =
                consultationExpertRepository.findByConsultationId(consultationId);
        return buildDetailResponse(consultation, allExperts);
    }

    public Map<String, Object> transferPresenter(Long consultationId,
                                                  Long fromUserId,
                                                  Long toUserId) {
        String lockKey = String.format(LOCK_TRANSFER_PRESENTER, consultationId);
        return distributedLock.executeWithLock(lockKey, LOCK_LEASE_MS,
                () -> doTransferPresenter(consultationId, fromUserId, toUserId));
    }

    @Transactional
    protected Map<String, Object> doTransferPresenter(Long consultationId,
                                                       Long fromUserId,
                                                       Long toUserId) {
        log.info("移交主讲人权限: consultationId={}, from={}, to={}",
                consultationId, fromUserId, toUserId);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        ConsultationExpert toExpert = consultationExpertRepository
                .findByConsultationIdAndExpertId(consultationId, toUserId)
                .orElseThrow(() -> new RuntimeException("目标专家未参与本次会诊"));

        if (!ConsultationExpertStatus.ACCEPTED.equals(toExpert.getStatus())
                && !ConsultationExpertStatus.JOINED.equals(toExpert.getStatus())) {
            throw new RuntimeException("目标专家尚未加入会诊，无法移交控制权");
        }

        boolean success = roomRouteService.transferPresenter(
                consultation.getRoomId(), fromUserId, toUserId, toExpert.getExpertName());

        if (!success) {
            throw new RuntimeException("移交控制权失败：您没有权限或目标专家不在房间中");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("fromUserId", fromUserId);
        payload.put("toUserId", toUserId);
        payload.put("toUserName", toExpert.getExpertName());
        payload.put("consultationId", consultationId);

        notificationService.sendRoomBroadcast(consultation.getRoomId(), payload);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("presenterId", toUserId);
        result.put("presenterName", toExpert.getExpertName());
        result.put("consultationId", consultationId);
        return result;
    }

    public Map<String, Object> getRoomControlState(Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));
        RoomRouteInfo roomInfo = roomRouteService.getRoom(consultation.getRoomId());

        Map<String, Object> state = new HashMap<>();
        state.put("consultationId", consultationId);
        state.put("roomId", consultation.getRoomId());
        if (roomInfo != null) {
            state.put("presenterId", roomInfo.getPresenterId());
            state.put("presenterName", roomInfo.getPresenterName());
            state.put("initiatorId", roomInfo.getInitiatorId());
            state.put("currentPresentationId", roomInfo.getCurrentPresentationId());
            state.put("currentPageNumber", roomInfo.getCurrentPageNumber());
            state.put("controlTakenAt", roomInfo.getControlTakenAt());
        }
        return state;
    }

    public RoomRouteInfo.ControlEvent broadcastControlEvent(Long consultationId,
                                                             Long operatorId,
                                                             String eventType,
                                                             Map<String, Object> payload) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new RuntimeException("会诊不存在"));

        RoomRouteInfo.ControlEvent event = roomRouteService.recordControlEvent(
                consultation.getRoomId(), operatorId, eventType, payload);

        if (event != null) {
            Map<String, Object> wsMsg = new HashMap<>();
            wsMsg.put("type", "CONTROL_EVENT");
            wsMsg.put("event", event);
            notificationService.sendRoomBroadcast(consultation.getRoomId(), wsMsg);
        } else {
            throw new RuntimeException("您没有控制权，无法执行该操作");
        }
        return event;
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
            ConsultationDetailResponse.ExpertInfo info =
                    new ConsultationDetailResponse.ExpertInfo();
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
