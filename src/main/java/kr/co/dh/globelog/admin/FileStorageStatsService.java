package kr.co.dh.globelog.admin;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.dh.globelog.chat.ChatAttachmentCleanupService;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.TripImage;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.file.FileStorageService;
import org.springframework.stereotype.Service;

/**
 * 관리자 전용 파일 저장 용량 통계(/admin/stats/storage). DB에 남아있는 파일 경로마다
 * 실제 스토리지(LOCAL 디스크 또는 SCP/SFTP)에 크기를 물어서 합산한다 — TripImage에는
 * 파일 크기 컬럼이 없고(기존 업로드 데이터를 소급 채울 방법이 없어 컬럼을 새로 추가하는
 * 대신 실제 스토리지를 직접 조회하는 쪽을 택함), ChatMessage.fileSize는 있지만 두 종류를
 * 같은 방식으로 다루는 게 일관돼서 여기서도 굳이 안 씀.
 *
 * 파일 수가 많아지면(특히 SCP 모드는 파일마다 원격 stat 호출) 느려질 수 있는 트레이드오프를
 * 감수한 것 — 관리자만 가끔 보는 화면이라 매 요청 실시간 계산으로도 충분하다고 판단.
 */
@Service
public class FileStorageStatsService {

    private final TripImageRepository tripImageRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FileStorageService fileStorageService;

    public FileStorageStatsService(TripImageRepository tripImageRepository,
            ChatMessageRepository chatMessageRepository, FileStorageService fileStorageService) {
        this.tripImageRepository = tripImageRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.fileStorageService = fileStorageService;
    }

    public FileStorageStatsResponse compute() {
        List<String> permanentPaths = tripImageRepository.findAll().stream()
                .map(TripImage::getFilePath)
                .toList();
        long permanentBytes = sumSizes(permanentPaths);

        List<ChatMessage> attachments = chatMessageRepository.findByFilePathIsNotNull();
        long chatAttachmentBytes = sumSizes(attachments.stream().map(ChatMessage::getFilePath).toList());

        LocalDateTime cleanupCutoff = LocalDateTime.now().minusMonths(ChatAttachmentCleanupService.RETENTION_MONTHS);
        List<ChatMessage> expiringSoon = chatMessageRepository.findByFilePathIsNotNullAndCreatedAtBetween(
                cleanupCutoff, cleanupCutoff.plusDays(7));
        long expiringSoonBytes = sumSizes(expiringSoon.stream().map(ChatMessage::getFilePath).toList());

        return new FileStorageStatsResponse(
                permanentBytes, permanentPaths.size(),
                chatAttachmentBytes, attachments.size(),
                expiringSoonBytes, expiringSoon.size());
    }

    private long sumSizes(List<String> relativePaths) {
        long total = 0;
        for (String relativePath : relativePaths) {
            total += fileStorageService.sizeOfOrZero(relativePath);
        }
        return total;
    }
}
