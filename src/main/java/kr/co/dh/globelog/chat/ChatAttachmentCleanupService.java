package kr.co.dh.globelog.chat;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.file.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 첨부파일은 3개월이 지나면 스토리지에서 지운다(대화방 파일이 무기한 쌓이는 걸
 * 막기 위한 보관정책). 메시지 행 자체는 대화 맥락(누가 언제 파일을 보냈는지)이 남아있게
 * 지우지 않고, ChatMessage.expireFile()로 filePath만 비워 "만료됨" 상태로 남긴다.
 */
@Service
public class ChatAttachmentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentCleanupService.class);

    // 관리자 파일 저장 용량 통계(FileStorageStatsService)가 "곧 삭제될 파일" 판단 기준으로
    // 이 값을 그대로 참조한다 — 정리 주기와 통계 화면의 기준이 어긋나지 않게 하기 위함.
    public static final int RETENTION_MONTHS = 3;

    private final ChatMessageRepository chatMessageRepository;
    private final FileStorageService fileStorageService;

    public ChatAttachmentCleanupService(ChatMessageRepository chatMessageRepository, FileStorageService fileStorageService) {
        this.chatMessageRepository = chatMessageRepository;
        this.fileStorageService = fileStorageService;
    }

    // 매일 새벽 3시 — 사용량이 적은 시간대에 돌려 스토리지 I/O 부담을 줄인다.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredAttachments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        List<ChatMessage> expired = chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBefore(cutoff);
        int deleted = 0;
        for (ChatMessage message : expired) {
            try {
                fileStorageService.delete(message.getFilePath());
                message.expireFile();
                deleted++;
            } catch (RuntimeException e) {
                // 삭제 실패는 filePath를 그대로 둬서 다음 실행에 다시 시도하게 한다
                // (여기서 expireFile()을 호출해버리면 스토리지에 파일이 남아있는 채로
                // DB만 "만료됨"으로 잘못 표시돼 orphan 파일이 영영 안 지워질 수 있음).
                log.warn("채팅 첨부파일 삭제 실패 (messageId={}, path={}): {}",
                        message.getId(), message.getFilePath(), e.getMessage());
            }
        }
        if (deleted > 0 || !expired.isEmpty()) {
            log.info("채팅 첨부파일 만료 정리: 대상 {}건 중 {}건 삭제", expired.size(), deleted);
        }
    }
}
