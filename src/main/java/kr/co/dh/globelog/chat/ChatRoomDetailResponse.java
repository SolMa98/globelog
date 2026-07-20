package kr.co.dh.globelog.chat;

import java.util.List;

public record ChatRoomDetailResponse(Long id, String type, String displayName, List<ChatRoomMemberResponse> members) {
}
