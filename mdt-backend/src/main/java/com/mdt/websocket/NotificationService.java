package com.mdt.websocket;

import com.mdt.dto.ConsultationNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Slf4j
@Service
public class NotificationService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    public void sendConsultationInvite(Long expertId, ConsultationNotification notification) {
        String destination = "/user/" + expertId + "/queue/consultation/invite";
        log.info("向专家 {} 发送会诊邀请通知: {}", expertId, notification.getTitle());
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(expertId),
                    "/queue/consultation/invite",
                    notification
            );
            log.info("会诊邀请通知已发送给专家 {}", expertId);
        } catch (Exception e) {
            log.error("发送会诊邀请通知失败, 专家ID: {}", expertId, e);
        }
    }

    public void sendExpertStatusUpdate(Long consultationId, Object expertStatus) {
        String destination = "/topic/consultation/" + consultationId + "/status";
        log.info("会诊 {} 专家状态更新: {}", consultationId, expertStatus);
        try {
            messagingTemplate.convertAndSend(destination, expertStatus);
        } catch (Exception e) {
            log.error("发送专家状态更新失败, 会诊ID: {}", consultationId, e);
        }
    }

    public void sendRoomBroadcast(String roomId, Object message) {
        String destination = "/topic/room/" + roomId;
        try {
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            log.error("发送房间广播消息失败, 房间ID: {}", roomId, e);
        }
    }

    public void sendUserMessage(Long userId, String destination, Object message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    destination,
                    message
            );
        } catch (Exception e) {
            log.error("发送用户消息失败, 用户ID: {}, destination: {}", userId, destination, e);
        }
    }

    public void broadcastConsultationEnded(Long consultationId) {
        String destination = "/topic/consultation/" + consultationId + "/ended";
        try {
            messagingTemplate.convertAndSend(destination, true);
        } catch (Exception e) {
            log.error("发送会诊结束通知失败, 会诊ID: {}", consultationId, e);
        }
    }
}
