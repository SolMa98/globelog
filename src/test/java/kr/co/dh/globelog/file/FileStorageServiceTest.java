package kr.co.dh.globelog.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.dh.globelog.file.storage.FileStorageProperties;
import kr.co.dh.globelog.file.storage.LocalFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    private static final byte[] REAL_PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] REAL_JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};

    private Path tempDir;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("globelog-upload-test");
        FileStorageProperties properties = new FileStorageProperties(
                FileStorageProperties.StorageMode.LOCAL, "globelog-test",
                new FileStorageProperties.Local(tempDir.toString()), null);
        fileStorageService = new FileStorageService(new LocalFileStorage(tempDir.toString()), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var files = Files.walk(tempDir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void fileWithValidPngSignatureIsStored() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", REAL_PNG_HEADER);

        String storedFilename = fileStorageService.store(file);

        assertThat(storedFilename).endsWith(".png");
        assertThat(tempDir.resolve(storedFilename)).exists();
    }

    @Test
    void textFileRenamedAsJpgIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "이건 이미지가 아니라 그냥 텍스트입니다".getBytes());

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");
    }

    @Test
    void imageWithMismatchedExtensionIsRejected() {
        // 확장자는 png이지만 실제 바이트는 JPEG 시그니처
        MockMultipartFile file = new MockMultipartFile(
                "file", "mismatch.png", "image/png", REAL_JPEG_HEADER);

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disallowedExtensionIsRejectedBeforeSignatureCheck() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "image/png", REAL_PNG_HEADER);

        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }
}
