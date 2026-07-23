package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.dh.globelog.chat.ChatAttachmentCleanupService;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.ChatRoom;
import kr.co.dh.globelog.domain.ChatRoomType;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImage;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 영구 저장(TripImage)과 3개월 만료 대상(ChatMessage 첨부)을 각각 다른 방식으로
 * 합산하는지, "일주일 내 삭제 예정"이 정리 주기(RETENTION_MONTHS) 컷오프를 기준으로
 * 올바른 구간을 조회하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageStatsServiceTest {

    @Mock
    private TripImageRepository tripImageRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private FileStorageService fileStorageService;

    private TripImage tripImage(String path) {
        Trip trip = new Trip(null, null, null, "t", java.time.LocalDate.now(), null, null);
        return new TripImage(trip, path, "photo.jpg", 0);
    }

    private ChatMessage fileMessage(String path) {
        ChatRoom room = new ChatRoom(ChatRoomType.DIRECT, null);
        User sender = new User("a@b.com", "pw", "tester");
        return ChatMessage.file(room, sender, path, "doc.pdf", 100);
    }

    @Test
    void 영구파일과_채팅첨부파일을_구분해서_합산한다() {
        when(tripImageRepository.findAll()).thenReturn(List.of(tripImage("p1"), tripImage("p2")));
        when(chatMessageRepository.findByFilePathIsNotNull()).thenReturn(List.of(fileMessage("c1")));
        when(chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBetween(any(), any())).thenReturn(List.of());
        when(fileStorageService.sizeOfOrZero("p1")).thenReturn(1000L);
        when(fileStorageService.sizeOfOrZero("p2")).thenReturn(2000L);
        when(fileStorageService.sizeOfOrZero("c1")).thenReturn(500L);

        FileStorageStatsService service =
                new FileStorageStatsService(tripImageRepository, chatMessageRepository, fileStorageService);
        FileStorageStatsResponse result = service.compute();

        assertThat(result.permanentBytes()).isEqualTo(3000L);
        assertThat(result.permanentCount()).isEqualTo(2L);
        assertThat(result.chatAttachmentBytes()).isEqualTo(500L);
        assertThat(result.chatAttachmentCount()).isEqualTo(1L);
    }

    @Test
    void 파일_크기_조회에_실패해도_0으로_처리되어_전체_집계가_깨지지_않는다() {
        when(tripImageRepository.findAll()).thenReturn(List.of(tripImage("missing")));
        when(chatMessageRepository.findByFilePathIsNotNull()).thenReturn(List.of());
        when(chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBetween(any(), any())).thenReturn(List.of());
        when(fileStorageService.sizeOfOrZero("missing")).thenReturn(0L);

        FileStorageStatsService service =
                new FileStorageStatsService(tripImageRepository, chatMessageRepository, fileStorageService);
        FileStorageStatsResponse result = service.compute();

        assertThat(result.permanentBytes()).isEqualTo(0L);
        assertThat(result.permanentCount()).isEqualTo(1L);
    }

    @Test
    void 만료임박_구간은_보관기간_컷오프에서_7일_뒤까지다() {
        when(tripImageRepository.findAll()).thenReturn(List.of());
        when(chatMessageRepository.findByFilePathIsNotNull()).thenReturn(List.of());
        when(chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBetween(any(), any()))
                .thenReturn(List.of(fileMessage("soon")));
        when(fileStorageService.sizeOfOrZero("soon")).thenReturn(777L);

        FileStorageStatsService service =
                new FileStorageStatsService(tripImageRepository, chatMessageRepository, fileStorageService);
        FileStorageStatsResponse result = service.compute();

        assertThat(result.expiringSoonBytes()).isEqualTo(777L);
        assertThat(result.expiringSoonCount()).isEqualTo(1L);

        java.time.LocalDateTime expectedFrom =
                java.time.LocalDateTime.now().minusMonths(ChatAttachmentCleanupService.RETENTION_MONTHS);
        java.time.LocalDateTime expectedTo = expectedFrom.plusDays(7);
        org.mockito.Mockito.verify(chatMessageRepository).findByFilePathIsNotNullAndCreatedAtBetween(
                org.mockito.ArgumentMatchers.argThat(from ->
                        java.time.Duration.between(from, expectedFrom).abs().getSeconds() < 5),
                org.mockito.ArgumentMatchers.argThat(to ->
                        java.time.Duration.between(to, expectedTo).abs().getSeconds() < 5));
    }
}
