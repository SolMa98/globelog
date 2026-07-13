package kr.co.dh.globelog.file.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 실제 바이트를 어디에 저장하는지(로컬 디스크 / 원격 SSH 서버)를 감추는 추상화.
 * relativePath는 항상 "/"로 구분된 상대 경로(예: globelog/2026/07/13/uuid.jpg)이고,
 * 구현체는 이를 각자의 베이스 디렉터리 기준으로 해석한다 — 상위 디렉터리 탈출(../) 방지는
 * 구현체 각자가 책임진다(FileStorageService에서도 한 번 더 검증해 이중으로 막는다).
 */
public interface FileStorage {

    void store(InputStream content, String relativePath) throws IOException;

    InputStream load(String relativePath) throws IOException;

    void delete(String relativePath) throws IOException;
}
