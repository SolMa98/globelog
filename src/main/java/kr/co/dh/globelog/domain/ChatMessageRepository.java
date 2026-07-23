package kr.co.dh.globelog.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최초 진입 시 최신 메시지부터 N개.
    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    // 위로 스크롤해서 더 과거 메시지를 불러올 때(무한스크롤 커서 방식).
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByCreatedAtDesc(Long roomId, Long beforeId, Pageable pageable);

    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);

    long countByRoomIdAndCreatedAtAfter(Long roomId, LocalDateTime after);

    long countByRoomId(Long roomId);

    // 통계 화면의 "채팅 메시지" 카드용 — soft-delete된 메시지도 "보낸 행위" 자체는
    // 있었으므로 포함해서 센다(deleted 여부로 거르지 않음).
    long countBySenderId(Long senderId);

    // 첨부파일 3개월 만료 정리용 — 아직 파일이 남아있는(filePath IS NOT NULL) 오래된 메시지.
    List<ChatMessage> findByFilePathIsNotNullAndCreatedAtBefore(LocalDateTime cutoff);

    // 관리자 파일 저장 용량 통계용 — 아직 만료 안 된(=스토리지에 실제로 남아있는) 첨부파일 전체.
    List<ChatMessage> findByFilePathIsNotNull();

    // 위 중 "이번 정리 주기(3개월) 컷오프로부터 향후 7일 안에" 만료될 것들만 별도로 집계.
    List<ChatMessage> findByFilePathIsNotNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
