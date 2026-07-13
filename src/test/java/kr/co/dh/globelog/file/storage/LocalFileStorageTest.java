package kr.co.dh.globelog.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path baseDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(baseDir.toString());
    }

    @Test
    void storedFileCanBeLoadedBack() throws IOException {
        storage.store(contentOf("hello"), "globelog/2026/07/13/a.jpg");

        String loaded = new String(storage.load("globelog/2026/07/13/a.jpg").readAllBytes(), StandardCharsets.UTF_8);

        assertThat(loaded).isEqualTo("hello");
    }

    @Test
    void nestedDirectoriesAreCreatedAutomatically() throws IOException {
        storage.store(contentOf("x"), "globelog/2026/07/13/nested.jpg");

        assertThat(baseDir.resolve("globelog/2026/07/13/nested.jpg")).exists();
    }

    @Test
    void deletedFileNoLongerLoads() throws IOException {
        storage.store(contentOf("x"), "globelog/2026/07/13/b.jpg");

        storage.delete("globelog/2026/07/13/b.jpg");

        assertThatThrownBy(() -> storage.load("globelog/2026/07/13/b.jpg"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> storage.store(contentOf("x"), "../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void absolutePathIsRejected() {
        assertThatThrownBy(() -> storage.store(contentOf("x"), "/etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    private static ByteArrayInputStream contentOf(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
