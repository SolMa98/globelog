package kr.co.dh.globelog.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.PasswordIdentityProvider;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.fs.SftpFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSH(SFTP)로 외부 서버의 특정 디렉터리에 파일을 저장/조회/삭제한다. "SCP"라고 부르지만
 * 내부적으로는 SFTP를 쓴다 — 옛 scp 프로토콜은 삭제(delete)를 지원하지 않아서, 삭제까지
 * 필요한 이 서비스에는 같은 SSH 채널로 store/load/delete를 전부 처리할 수 있는 SFTP가
 * 더 적합하다(둘 다 SSH 위에서 동작하는 "안전한 원격 파일 복사"라는 목적은 동일).
 *
 * 호스트 키 검증(known_hosts)을 기본으로 강제한다 — 등록되지 않은 서버는 거부해서
 * 중간자 공격(MITM)으로 엉뚱한 서버에 파일이 새거나 위조되는 것을 막는다.
 * strict-host-key-checking=false는 known_hosts를 아직 준비 못 한 경우의 임시 탈출구일 뿐,
 * 운영에서는 쓰지 않는 게 안전하다.
 */
public class ScpFileStorage implements FileStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScpFileStorage.class);

    private final FileStorageProperties.Scp config;
    private final SshClient sshClient;
    private final Object connectionLock = new Object();
    private ClientSession session;
    private SftpFileSystem fileSystem;

    public ScpFileStorage(FileStorageProperties.Scp config) {
        this.config = config;
        this.sshClient = SshClient.setUpDefaultClient();
        this.sshClient.setServerKeyVerifier(buildServerKeyVerifier(config));
        this.sshClient.start();
        connect();
        try {
            Files.createDirectories(remoteBaseDir());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "원격 베이스 디렉터리를 생성할 수 없습니다: " + config.remoteDir(), e);
        }
    }

    @Override
    public void store(InputStream content, String relativePath) throws IOException {
        ensureConnected();
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream load(String relativePath) throws IOException {
        ensureConnected();
        return Files.newInputStream(resolve(relativePath));
    }

    @Override
    public void delete(String relativePath) throws IOException {
        ensureConnected();
        Files.deleteIfExists(resolve(relativePath));
    }

    @Override
    public long size(String relativePath) throws IOException {
        ensureConnected();
        return Files.size(resolve(relativePath));
    }

    @Override
    public void close() {
        synchronized (connectionLock) {
            closeQuietly(fileSystem);
            closeQuietly(session);
        }
        sshClient.stop();
    }

    private Path resolve(String relativePath) {
        RelativePathValidator.validate(relativePath);
        Path base = remoteBaseDir();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("베이스 디렉터리를 벗어난 경로입니다: " + relativePath);
        }
        return resolved;
    }

    private Path remoteBaseDir() {
        return fileSystem.getPath(config.remoteDir());
    }

    private void ensureConnected() {
        synchronized (connectionLock) {
            if (session != null && session.isOpen() && fileSystem != null && fileSystem.isOpen()) {
                return;
            }
            log.warn("SCP(SFTP) 연결이 끊어져 재연결을 시도합니다: {}:{}", config.host(), config.port());
            closeQuietly(fileSystem);
            closeQuietly(session);
            connect();
        }
    }

    private void connect() {
        try {
            ClientSession newSession = sshClient.connect(config.username(), config.host(), config.port())
                    .verify(Duration.ofSeconds(config.connectTimeoutSeconds()))
                    .getSession();
            authenticate(newSession);
            newSession.auth().verify(Duration.ofSeconds(config.connectTimeoutSeconds()));
            this.session = newSession;
            this.fileSystem = SftpClientFactory.instance().createSftpFileSystem(newSession);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "SCP 저장소(" + config.host() + ":" + config.port() + ") 연결에 실패했습니다. "
                            + "호스트/포트/인증 정보 또는 known_hosts 등록 여부를 확인하세요.", e);
        }
    }

    private void authenticate(ClientSession clientSession) throws IOException, GeneralSecurityException {
        if (config.authMethod() == FileStorageProperties.Scp.AuthMethod.PRIVATE_KEY) {
            FileKeyPairProvider keyPairProvider = new FileKeyPairProvider(Path.of(config.privateKeyPath()));
            if (config.privateKeyPassphrase() != null && !config.privateKeyPassphrase().isBlank()) {
                keyPairProvider.setPasswordFinder(FilePasswordProvider.of(config.privateKeyPassphrase()));
            }
            keyPairProvider.loadKeys(clientSession).forEach(clientSession::addPublicKeyIdentity);
        } else {
            clientSession.setPasswordIdentityProvider(PasswordIdentityProvider.wrapPasswords(config.password()));
        }
    }

    private static ServerKeyVerifier buildServerKeyVerifier(FileStorageProperties.Scp config) {
        if (!config.strictHostKeyChecking()) {
            log.warn("SCP strict-host-key-checking이 꺼져 있습니다 — 운영 환경에서는 권장하지 않습니다.");
            return AcceptAllServerKeyVerifier.INSTANCE;
        }
        // known_hosts에 등록된 호스트 키만 신뢰하고, 그 외(미등록 호스트)는 전부 거부한다.
        return new KnownHostsServerKeyVerifier(
                RejectAllServerKeyVerifier.INSTANCE, Path.of(config.knownHostsPath()));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.debug("SCP 연결 종료 중 예외(무시)", e);
        }
    }
}
