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

    // 첨부파일 3개월 만료 정리용 — 아직 파일이 남아있는(filePath IS NOT NULL) 오래된 메시지.
    List<ChatMessage> findByFilePathIsNotNullAndCreatedAtBefore(LocalDateTime cutoff);
}
