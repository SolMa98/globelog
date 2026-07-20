package kr.co.dh.globelog.chat;

import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageType;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String type,
        String content,
        String fileUrl,
        String originalFilename,
        Long fileSize,
        boolean fileExpired,
        LocalDateTime createdAt,
        boolean self) {

    public static ChatMessageResponse from(ChatMessage message, Long viewerId) {
        var sender = message.getSender();
        boolean expired = message.getType() == ChatMessageType.FILE && message.getFilePath() == null;
        String fileUrl = message.getFilePath() != null ? "/uploads/" + message.getFilePath() : null;
        return new ChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                sender.getId(),
                sender.getNickname(),
                sender.getProfileImageUrl(),
                message.getType().name(),
                message.getContent(),
                fileUrl,
                message.getOriginalFilename(),
                message.getFileSize(),
                expired,
                message.getCreatedAt(),
                sender.getId().equals(viewerId));
    }
}
