package com.mdt.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        RoomRouteInfo roomInfo = getRoom(roomId);
        if (roomInfo != null) {
            roomInfo.setStatus(status);
            roomInfo.setUpdatedAt(System.currentTimeMillis());
            String key = roomPrefix + roomId;
            redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
        }
    }

    public void addParticipant(String roomId, ParticipantInfo participant) {
        RoomRouteInfo roomInfo = getRoom(roomId);
        if (roomInfo != null) {
            participant.setJoinedAt(System.currentTimeMillis());
            participant.setStatus("JOINED");
            if (participant.getAudioEnabled() == null) {
                participant.setAudioEnabled(true);
            }
            if (participant.getVideoEnabled() == null) {
                participant.setVideoEnabled(true);
            }
            roomInfo.getParticipants().put(participant.getUserId(), participant);
            roomInfo.setUpdatedAt(System.currentTimeMillis());
            String key = roomPrefix + roomId;
            redisTemplate.opsForValue().set(key, roomInfo, sessionTtl, TimeUnit.SECONDS);
        }
    }

    public void removeParticipant(String roomId, Long userId) {
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
        }
    }

    public Map<Long, ParticipantInfo> getParticipants(String roomId) {
        RoomRouteInfo roomInfo = getRoom(roomId);
        return roomInfo != null ? roomInfo.getParticipants() : new HashMap<>();
    }

    public void setUserRoute(String userId, String roomId) {
        String key = routePrefix + userId;
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
    }

    public void refreshRoomTtl(String roomId) {
        String key = roomPrefix + roomId;
        redisTemplate.expire(key, sessionTtl, TimeUnit.SECONDS);
    }

    public Set<String> getAllActiveRooms() {
        return redisTemplate.keys(roomPrefix + "*");
    }
}
