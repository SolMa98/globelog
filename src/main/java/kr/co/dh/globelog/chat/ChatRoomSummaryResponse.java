package kr.co.dh.globelog.chat;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
        Long id,
        String type,
        String displayName,
        String displayImageUrl,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        long unreadCount) {
}
