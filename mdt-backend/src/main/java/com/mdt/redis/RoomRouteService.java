package com.mdt.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
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

    public void createRoom(String roomId, Long consultationId, String consultationNo) {
        RoomRouteInfo roomInfo = new RoomRouteInfo();
        roomInfo.setRoomId(roomId);
        roomInfo.setConsultationId(consultationId);
        roomInfo.setConsultationNo(consultationNo);
        roomInfo.setStatus("CREATED");
        roomInfo.setParticipants(new HashMap<>());
        roomInfo.setCreatedAt(System.currentTimeMillis());
        roomInfo.setUpdatedAt(System.currentTimeMillis());

        String key = roomPrefix + roomId;
        redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
        log.debug("创建房间路由: roomId={}, consultationId={}", roomId, consultationId);
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
