package kr.co.dh.globelog.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 프로젝트 디렉터리 밖의 로컬 파일시스템 경로에 저장하는 구현체.
 * base-dir은 기본값부터(${user.home}/globelog-uploads) 프로젝트 외부를 가리키도록
 * application.properties에 설정돼 있다 — 배포 서버 재배포/git clean 등으로 업로드 파일이
 * 실수로 날아가는 걸 막기 위함.
 */
public class LocalFileStorage implements FileStorage {

    private final Path baseDir;

    public LocalFileStorage(String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 베이스 디렉터리를 생성할 수 없습니다: " + this.baseDir, e);
        }
    }

    @Override
    public void store(InputStream content, String relativePath) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream load(String relativePath) throws IOException {
        return Files.newInputStream(resolve(relativePath));
    }

    @Override
    public void delete(String relativePath) throws IOException {
        Files.deleteIfExists(resolve(relativePath));
    }

    private Path resolve(String relativePath) {
        RelativePathValidator.validate(relativePath);
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("베이스 디렉터리를 벗어난 경로입니다: " + relativePath);
        }
        return resolved;
    }
}
