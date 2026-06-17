package com.mdt.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RoomRouteService {

    @Value("${mdt.webrtc.room-prefix}")
    private String roomPrefix;

    @Value("${mdt.webrtc.route-prefix}")
    private String routePrefix;

    @Value("${mdt.webrtc.session-ttl}")
    private long sessionTtl;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private DistributedLock distributedLock;

    private static final String ROOM_LOCK_KEY = "room:%s";
    private static final long ROOM_LOCK_LEASE_MS = 3000L;

    public void createRoom(String roomId, Long consultationId, String consultationNo,
                           Long initiatorId, String initiatorName) {
        RoomRouteInfo roomInfo = new RoomRouteInfo();
        roomInfo.setRoomId(roomId);
        roomInfo.setConsultationId(consultationId);
        roomInfo.setConsultationNo(consultationNo);
        roomInfo.setStatus("CREATED");
        roomInfo.setParticipants(new HashMap<>());
        roomInfo.setRecentEvents(new ArrayList<>());

        roomInfo.setInitiatorId(initiatorId);
        roomInfo.setPresenterId(initiatorId);
        roomInfo.setPresenterName(initiatorName);
        roomInfo.setCurrentPageNumber(1);
        roomInfo.setControlTakenAt(System.currentTimeMillis());

        roomInfo.setCreatedAt(System.currentTimeMillis());
        roomInfo.setUpdatedAt(System.currentTimeMillis());

        String key = roomPrefix + roomId;
        redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
        log.debug("创建房间路由: roomId={}, consultationId={}, initiator={}",
                roomId, consultationId, initiatorName);
    }

    public void initPresenterIfAbsent(String roomId, Long userId, String userName) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            RoomRouteInfo roomInfo = getRoom(roomId);
            if (roomInfo != null && roomInfo.getPresenterId() == null) {
                roomInfo.setPresenterId(userId);
                roomInfo.setPresenterName(userName);
                roomInfo.setControlTakenAt(System.currentTimeMillis());
                roomInfo.setUpdatedAt(System.currentTimeMillis());
                String key = roomPrefix + roomId;
                redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
                log.info("初始化房间主讲人: roomId={}, presenter={}", roomId, userName);
            }
        });
    }

    public boolean transferPresenter(String roomId, Long fromUserId, Long toUserId, String toUserName) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        final boolean[] result = {false};
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            RoomRouteInfo roomInfo = getRoom(roomId);
            if (roomInfo == null) {
                return;
            }

            Long currentPresenter = roomInfo.getPresenterId();
            Long initiator = roomInfo.getInitiatorId();

            boolean hasPermission =
                    (currentPresenter != null && currentPresenter.equals(fromUserId))
                            || (initiator != null && initiator.equals(fromUserId));

            if (!hasPermission) {
                log.warn("用户 {} 无权移交主讲人权限, 当前主讲人={}, 发起人={}",
                        fromUserId, currentPresenter, initiator);
                return;
            }

            if (roomInfo.getParticipants() == null
                    || !roomInfo.getParticipants().containsKey(toUserId)) {
                log.warn("目标用户 {} 不在房间 {} 中, 无法移交主讲人", toUserId, roomId);
                return;
            }

            roomInfo.setPresenterId(toUserId);
            roomInfo.setPresenterName(toUserName);
            roomInfo.setControlTakenAt(System.currentTimeMillis());
            roomInfo.setUpdatedAt(System.currentTimeMillis());

            RoomRouteInfo.ControlEvent event = new RoomRouteInfo.ControlEvent();
            event.setEventType("PRESENTER_TRANSFERRED");
            event.setOperatorId(fromUserId);
            event.setOperatorName(roomInfo.getParticipants().get(fromUserId) != null
                    ? roomInfo.getParticipants().get(fromUserId).getUserName()
                    : "system");
            event.setTimestamp(System.currentTimeMillis());
            event.setPayload(Map.of(
                    "fromUserId", fromUserId,
                    "toUserId", toUserId,
                    "toUserName", toUserName
            ));
            appendEventLocked(roomInfo, event);

            String key = roomPrefix + roomId;
            redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
            result[0] = true;
            log.info("主讲人权限移交成功: roomId={}, {} -> {}", roomId, fromUserId, toUserName);
        });
        return result[0];
    }

    public RoomRouteInfo.ControlEvent recordControlEvent(String roomId, Long operatorId,
                                                         String eventType,
                                                         Map<String, Object> payload) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        final RoomRouteInfo.ControlEvent[] eventWrapper = {null};

        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            RoomRouteInfo roomInfo = getRoom(roomId);
            if (roomInfo == null) return;

            Long presenterId = roomInfo.getPresenterId();
            Long initiatorId = roomInfo.getInitiatorId();

            boolean isPresenterOrInitiator =
                    (presenterId != null && presenterId.equals(operatorId))
                            || (initiatorId != null && initiatorId.equals(operatorId));

            if (!isPresenterOrInitiator) {
                log.warn("用户 {} 非主讲人/发起人, 无权广播控制事件 {}", operatorId, eventType);
                return;
            }

            String operatorName = roomInfo.getParticipants() != null
                    && roomInfo.getParticipants().get(operatorId) != null
                    ? roomInfo.getParticipants().get(operatorId).getUserName()
                    : "unknown";

            RoomRouteInfo.ControlEvent event = new RoomRouteInfo.ControlEvent();
            event.setEventType(eventType);
            event.setOperatorId(operatorId);
            event.setOperatorName(operatorName);
            event.setTimestamp(System.currentTimeMillis());
            event.setPayload(payload != null ? payload : Map.of());
            appendEventLocked(roomInfo, event);

            if ("PAGE_FLIP".equals(eventType) && payload != null && payload.get("pageNumber") != null) {
                try {
                    roomInfo.setCurrentPageNumber(Integer.parseInt(payload.get("pageNumber").toString()));
                } catch (NumberFormatException ignored) {
                }
            }
            if ("PRESENTATION_SWITCH".equals(eventType) && payload != null && payload.get("presentationId") != null) {
                roomInfo.setCurrentPresentationId(payload.get("presentationId").toString());
            }

            roomInfo.setUpdatedAt(System.currentTimeMillis());
            String key = roomPrefix + roomId;
            redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
            eventWrapper[0] = event;
            log.debug("记录控制事件: roomId={}, type={}, operator={}", roomId, eventType, operatorName);
        });
        return eventWrapper[0];
    }

    private void appendEventLocked(RoomRouteInfo roomInfo, RoomRouteInfo.ControlEvent event) {
        if (roomInfo.getRecentEvents() == null) {
            roomInfo.setRecentEvents(new ArrayList<>());
        }
        roomInfo.getRecentEvents().add(event);
        if (roomInfo.getRecentEvents().size() > 100) {
            roomInfo.setRecentEvents(new ArrayList<>(
                    roomInfo.getRecentEvents().subList(
                            roomInfo.getRecentEvents().size() - 100,
                            roomInfo.getRecentEvents().size()
                    )
            ));
        }
    }

    public RoomRouteInfo getRoom(String roomId) {
        String key = roomPrefix + roomId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof RoomRouteInfo) {
            return (RoomRouteInfo) obj;
        }
        return null;
    }

    public void updateRoomStatus(String roomId, String status) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            RoomRouteInfo roomInfo = getRoom(roomId);
            if (roomInfo != null) {
                roomInfo.setStatus(status);
                roomInfo.setUpdatedAt(System.currentTimeMillis());
                String key = roomPrefix + roomId;
                redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
            }
        });
    }

    public void addParticipant(String roomId, ParticipantInfo participant) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            doAddParticipant(roomId, participant, false);
        });
    }

    public void addParticipantIfAbsent(String roomId, ParticipantInfo participant) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            doAddParticipant(roomId, participant, true);
        });
    }

    private void doAddParticipant(String roomId, ParticipantInfo participant, boolean ifAbsent) {
        RoomRouteInfo roomInfo = getRoom(roomId);
        if (roomInfo == null) {
            log.warn("房间不存在, 无法添加参与者: roomId={}, userId={}", roomId, participant.getUserId());
            return;
        }

        Map<Long, ParticipantInfo> participants = roomInfo.getParticipants();
        if (participants == null) {
            participants = new HashMap<>();
            roomInfo.setParticipants(participants);
        }

        Long userId = participant.getUserId();
        if (ifAbsent && participants.containsKey(userId)) {
            ParticipantInfo existing = participants.get(userId);
            if (existing != null && !"LEFT".equals(existing.getStatus())) {
                log.debug("参与者已存在于房间中, 跳过重复添加: roomId={}, userId={}, status={}",
                        roomId, userId, existing.getStatus());
                return;
            }
        }

        participant.setJoinedAt(System.currentTimeMillis());
        if (participant.getStatus() == null) {
            participant.setStatus("JOINED");
        }
        if (participant.getAudioEnabled() == null) {
            participant.setAudioEnabled(true);
        }
        if (participant.getVideoEnabled() == null) {
            participant.setVideoEnabled(true);
        }

        participants.put(userId, participant);
        roomInfo.setUpdatedAt(System.currentTimeMillis());

        String key = roomPrefix + roomId;
        redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
        log.debug("添加参与者到房间: roomId={}, userId={}, ifAbsent={}", roomId, userId, ifAbsent);
    }

    public void removeParticipant(String roomId, Long userId) {
        String lockKey = String.format(ROOM_LOCK_KEY, roomId);
        distributedLock.executeWithLock(lockKey, ROOM_LOCK_LEASE_MS, () -> {
            RoomRouteInfo roomInfo = getRoom(roomId);
            if (roomInfo != null && roomInfo.getParticipants() != null) {
                ParticipantInfo participant = roomInfo.getParticipants().get(userId);
                if (participant != null) {
                    participant.setStatus("LEFT");
                }
                roomInfo.getParticipants().remove(userId);
                roomInfo.setUpdatedAt(System.currentTimeMillis());
                String key = roomPrefix + roomId;
                redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
                log.debug("从房间移除参与者: roomId={}, userId={}", roomId, userId);
            }
        });
    }

    public Map<Long, ParticipantInfo> getParticipants(String roomId) {
        RoomRouteInfo roomInfo = getRoom(roomId);
        return roomInfo != null && roomInfo.getParticipants() != null
                ? roomInfo.getParticipants()
                : new HashMap<>();
    }

    public void setUserRoute(String userId, String roomId) {
        String key = routePrefix + userId;
        String existing = getUserRoom(userId);
        if (existing != null && !existing.equals(roomId)) {
            log.warn("用户 {} 的路由从 {} 切换到 {}", userId, existing, roomId);
        }
        redisTemplate.opsForValue().set(key, roomId, sessionTtl, TimeUnit.SECONDS);
    }

    public String getUserRoom(String userId) {
        String key = routePrefix + userId;
        Object obj = redisTemplate.opsForValue().get(key);
        return obj != null ? obj.toString() : null;
    }

    public void clearUserRoute(String userId) {
        String key = routePrefix + userId;
        redisTemplate.delete(key);
    }

    public void deleteRoom(String roomId) {
        String key = roomPrefix + roomId;
        RoomRouteInfo roomInfo = getRoom(roomId);
        if (roomInfo != null && roomInfo.getParticipants() != null) {
            for (Long userId : roomInfo.getParticipants().keySet()) {
                clearUserRoute(userId.toString());
            }
        }
        redisTemplate.delete(key);
        log.info("删除房间路由: roomId={}", roomId);
    }

    public void refreshRoomTtl(String roomId) {
        String key = roomPrefix + roomId;
        redisTemplate.expire(key, sessionTtl, TimeUnit.SECONDS);
    }

    public Set<String> getAllActiveRooms() {
        return redisTemplate.keys(roomPrefix + "*");
    }
}
