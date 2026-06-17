package com.mdt.redis;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class RoomRouteInfo implements Serializable {
    private String roomId;
    private Long consultationId;
    private String consultationNo;
    private String status;
    private Map<Long, ParticipantInfo> participants;
    private Long createdAt;
    private Long updatedAt;
}
