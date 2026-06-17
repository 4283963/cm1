package com.mdt.redis;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class RoomRouteInfo implements Serializable {
    private String roomId;
    private Long consultationId;
    private String consultationNo;
    private String status;
    private Map<Long, ParticipantInfo> participants;

    private Long presenterId;
    private String presenterName;
    private Long initiatorId;

    private String currentPresentationId;
    private Integer currentPageNumber;
    private Long controlTakenAt;

    private List<ControlEvent> recentEvents;

    private Long createdAt;
    private Long updatedAt;

    @Data
    public static class ControlEvent implements Serializable {
        private String eventType;
        private Long operatorId;
        private String operatorName;
        private Long timestamp;
        private Map<String, Object> payload;
    }
}
