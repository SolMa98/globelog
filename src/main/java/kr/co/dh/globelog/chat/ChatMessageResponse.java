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
        boolean deleted,
        boolean edited,
        LocalDateTime createdAt,
        boolean self) {

    public static ChatMessageResponse from(ChatMessage message, Long viewerId) {
        var sender = message.getSender();
        // 보관기간 만료(fileExpired)와 작성자 삭제(deleted)는 화면에 다른 문구로 보여주므로
        // 구분해서 내려준다 — 둘 다 filePath가 null이 되는 건 같지만 의미가 다르다.
        boolean expired = !message.isDeleted() && message.getType() == ChatMessageType.FILE && message.getFilePath() == null;
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
                message.isDeleted(),
                message.getEditedAt() != null,
                message.getCreatedAt(),
                sender.getId().equals(viewerId));
    }
}
