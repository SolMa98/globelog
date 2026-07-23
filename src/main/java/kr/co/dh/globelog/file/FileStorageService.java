package kr.co.dh.globelog.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kr.co.dh.globelog.file.storage.FileStorage;
import kr.co.dh.globelog.file.storage.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    // 채팅 첨부파일은 사진 외에 문서도 허용한다(여행 사진 업로드보다 넓은 범위).
    private static final Set<String> ALLOWED_CHAT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt");

    // 확장자/Content-Type은 클라이언트가 임의로 조작해서 보낼 수 있으므로, 파일 앞부분의
    // 실제 시그니처(매직바이트)까지 대조해야 "확장자만 .jpg로 바꾼 임의 파일" 업로드를 막을 수 있다.
    private static final int SIGNATURE_CHECK_BYTES = 12;

    private final FileStorage fileStorage;
    private final String projectName;

    public FileStorageService(FileStorage fileStorage, FileStorageProperties properties) {
        this.fileStorage = fileStorage;
        this.projectName = properties.projectName();
    }

    /**
     * 이미지 파일을 검증 후 "프로젝트명/년/월/일/UUID.확장자" 상대 경로로 저장하고,
     * /uploads/** 로 서빙될 그 상대 경로를 반환한다.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + extension);
        }
        if (!hasValidSignature(readHeader(file), extension)) {
            throw new IllegalArgumentException("파일 내용이 확장자(" + extension + ")와 일치하지 않습니다.");
        }

        String relativePath = buildRelativePath(extension);
        try (InputStream in = file.getInputStream()) {
            fileStorage.store(in, relativePath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }

        return relativePath;
    }

    /**
     * 채팅 첨부파일(이미지 + 문서) 검증 후 저장 — store()와 저장 경로 규칙은 같고
     * 허용 확장자/시그니처 검사만 문서까지 넓힌 버전.
     */
    public String storeChatAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_CHAT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + extension);
        }
        if (!hasValidChatSignature(readHeader(file), extension)) {
            throw new IllegalArgumentException("파일 내용이 확장자(" + extension + ")와 일치하지 않습니다.");
        }

        String relativePath = buildRelativePath(extension);
        try (InputStream in = file.getInputStream()) {
            fileStorage.store(in, relativePath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }

        return relativePath;
    }

    public InputStream load(String relativePath) throws IOException {
        return fileStorage.load(relativePath);
    }

    public void delete(String relativePath) {
        try {
            fileStorage.delete(relativePath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다: " + relativePath, e);
        }
    }

    // 관리자 파일 저장 용량 통계용. DB에는 경로만 있고 존재 여부가 보장되지 않는 레코드가
    // 섞여 있을 수 있어(수동 삭제, 마이그레이션 누락 등) 예외 시 0을 반환하고 넘어간다 —
    // 파일 하나 조회 실패로 통계 화면 전체가 깨지면 안 되기 때문.
    public long sizeOfOrZero(String relativePath) {
        try {
            return fileStorage.size(relativePath);
        } catch (IOException e) {
            log.warn("파일 크기 조회 실패(통계에서 0으로 처리): {}", relativePath, e);
            return 0L;
        }
    }

    private String buildRelativePath(String extension) {
        LocalDate today = LocalDate.now();
        return "%s/%04d/%02d/%02d/%s.%s".formatted(
                projectName, today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(), extension);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        return extension.toLowerCase(Locale.ROOT);
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_CHECK_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽을 수 없습니다.", e);
        }
    }

    private boolean hasValidSignature(byte[] header, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(header, 'G', 'I', 'F', '8') && (header.length > 4 && (header[4] == '7' || header[4] == '9'));
            case "webp" -> startsWith(header, 'R', 'I', 'F', 'F') && header.length >= 12
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    // 문서 포맷 매직바이트 — docx/xlsx/pptx는 zip 컨테이너(OOXML)라 PK 시그니처를 공유하고,
    // doc/xls/ppt(구버전 바이너리)는 OLE 복합문서 시그니처를 공유한다. txt는 일반 텍스트라
    // 신뢰할 만한 시그니처가 없어 확장자 일치만으로 통과시킨다(플레인 텍스트라 실행 위험 없음).
    private boolean hasValidChatSignature(byte[] header, String extension) {
        return switch (extension) {
            case "jpg", "jpeg", "png", "gif", "webp" -> hasValidSignature(header, extension);
            case "pdf" -> startsWith(header, '%', 'P', 'D', 'F');
            case "docx", "xlsx", "pptx" -> startsWith(header, 0x50, 0x4B, 0x03, 0x04);
            case "doc", "xls", "ppt" -> startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "txt" -> true;
            default -> false;
        };
    }

    private boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != (expected[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
