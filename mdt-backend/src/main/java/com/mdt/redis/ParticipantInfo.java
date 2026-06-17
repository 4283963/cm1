package com.mdt.redis;

import lombok.Data;

import java.io.Serializable;

@Data
public class ParticipantInfo implements Serializable {
    private Long userId;
    private String userName;
    private String departmentName;
    private String role;
    private String streamId;
    private String sdpOffer;
    private String sdpAnswer;
    private String status;
    private Long joinedAt;
    private Boolean audioEnabled;
    private Boolean videoEnabled;
}
