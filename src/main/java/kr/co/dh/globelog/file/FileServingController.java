package kr.co.dh.globelog.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * /uploads/** 정적 리소스 매핑 대신 컨트롤러로 서빙한다 — FileStorage 구현체(LOCAL/SCP)가
 * 무엇이든 FileStorageService.load()만 거치면 되도록 통일하기 위함(SCP 모드는 원격 서버라
 * 정적 파일 핸들러로 직접 매핑할 수 없음).
 */
@RestController
public class FileServingController {

    private final FileStorageService fileStorageService;

    public FileServingController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/uploads/{*relativePath}")
    public ResponseEntity<InputStreamResource> serve(@PathVariable String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;

        InputStream content;
        try {
            content = fileStorageService.load(path);
        } catch (NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return ResponseEntity.ok()
                .contentType(guessContentType(path))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new InputStreamResource(content));
    }

    private MediaType guessContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
