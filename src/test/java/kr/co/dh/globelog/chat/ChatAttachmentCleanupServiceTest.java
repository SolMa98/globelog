package kr.co.dh.globelog.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.ChatRoom;
import kr.co.dh.globelog.domain.ChatRoomType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * "채팅 첨부파일만 3개월 뒤 자동 삭제되고, 여행/게시글 사진은 보관기간 없이 그대로
 * 남아야 한다"는 보관정책 경계를 검증한다. 이 서비스는 생성자에 ChatMessageRepository만
 * 받고 TripImageRepository는 아예 주입받지 않으므로, 여행 사진 삭제는 구조적으로
 * 불가능하다 — 나중에 누군가 실수로 여기에 TripImageRepository를 끌어와 확장하면
 * 이 테스트의 의도(채팅 전용)를 다시 확인해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatAttachmentCleanupServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private FileStorageService fileStorageService;

    private ChatAttachmentCleanupService service;

    private ChatMessage expiredFileMessage() {
        ChatRoom room = new ChatRoom(ChatRoomType.DIRECT, null);
        User sender = new User("a@b.com", "pw", "tester");
        return ChatMessage.file(room, sender, "globelog/2026/01/01/abc.jpg", "abc.jpg", 1024);
    }

    @Test
    void 삭제에_성공하면_filePath를_비워_만료로_표시한다() {
        service = new ChatAttachmentCleanupService(chatMessageRepository, fileStorageService);
        ChatMessage message = expiredFileMessage();
        when(chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBefore(any())).thenReturn(List.of(message));

        service.cleanupExpiredAttachments();

        verify(fileStorageService).delete("globelog/2026/01/01/abc.jpg");
        assertThat(message.getFilePath()).isNull();
    }

    @Test
    void 삭제가_실패하면_filePath를_그대로_둬서_다음번에_재시도한다() {
        service = new ChatAttachmentCleanupService(chatMessageRepository, fileStorageService);
        ChatMessage message = expiredFileMessage();
        when(chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBefore(any())).thenReturn(List.of(message));
        doThrow(new RuntimeException("스토리지 연결 실패")).when(fileStorageService).delete(anyString());

        service.cleanupExpiredAttachments();

        assertThat(message.getFilePath()).isEqualTo("globelog/2026/01/01/abc.jpg");
    }
}
