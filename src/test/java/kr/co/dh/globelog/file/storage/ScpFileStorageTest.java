package kr.co.dh.globelog.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.List;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 임베디드 SFTP 테스트 서버(로컬)로 ScpFileStorage를 검증한다 — 실제 원격 서버가 아직
 * 없어서(사용자 확인) 이 방식으로 store/load/delete와 두 인증 방식(PASSWORD/PRIVATE_KEY),
 * known_hosts 미등록 호스트 거부(보안 요구사항)까지 실제 SSH 핸드셰이크로 검증한다.
 */
class ScpFileStorageTest {

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpass";

    private static SshServer sshServer;
    private static Path serverRoot;
    private static KeyPair clientKeyPair;
    private static Path clientPrivateKeyFile;

    @TempDir
    static Path sharedTempDir;

    @BeforeAll
    static void startServer() throws Exception {
        serverRoot = Files.createTempDirectory("scp-test-server-root");

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        clientKeyPair = keyPairGenerator.generateKeyPair();
        clientPrivateKeyFile = Files.createTempFile("scp-test-client-key", "");
        try (OutputStream out = Files.newOutputStream(clientPrivateKeyFile)) {
            new OpenSSHKeyPairResourceWriter().writePrivateKey(clientKeyPair, "scp-test", null, out);
        }

        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0);
        sshServer.setKeyPairProvider(
                new SimpleGeneratorHostKeyProvider(Files.createTempFile("scp-test-host-key", "")));
        sshServer.setPasswordAuthenticator(
                (username, password, session) -> USERNAME.equals(username) && PASSWORD.equals(password));
        sshServer.setPublickeyAuthenticator(
                (username, key, session) -> USERNAME.equals(username) && samePublicKey(key, clientKeyPair.getPublic()));
        sshServer.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(serverRoot));
        sshServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        sshServer.stop(true);
    }

    @Test
    void storedFileCanBeLoadedBack() throws IOException {
        try (ScpFileStorage storage = new ScpFileStorage(passwordConfig(insecureKnownHosts()))) {
            storage.store(contentOf("hello"), "globelog/2026/07/13/a.jpg");

            String loaded = new String(
                    storage.load("globelog/2026/07/13/a.jpg").readAllBytes(), StandardCharsets.UTF_8);

            assertThat(loaded).isEqualTo("hello");
        }
    }

    @Test
    void deletedFileNoLongerLoads() throws IOException {
        try (ScpFileStorage storage = new ScpFileStorage(passwordConfig(insecureKnownHosts()))) {
            storage.store(contentOf("bye"), "globelog/2026/07/13/b.jpg");

            storage.delete("globelog/2026/07/13/b.jpg");

            assertThatThrownBy(() -> storage.load("globelog/2026/07/13/b.jpg")).isInstanceOf(IOException.class);
        }
    }

    @Test
    void pathTraversalIsRejected() throws IOException {
        try (ScpFileStorage storage = new ScpFileStorage(passwordConfig(insecureKnownHosts()))) {
            assertThatThrownBy(() -> storage.store(contentOf("x"), "../../etc/passwd"))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void privateKeyAuthenticationWorks() throws IOException {
        FileStorageProperties.Scp config = new FileStorageProperties.Scp(
                "127.0.0.1", sshServer.getPort(), USERNAME,
                FileStorageProperties.Scp.AuthMethod.PRIVATE_KEY,
                null, clientPrivateKeyFile.toString(), null,
                serverRoot.toString(), insecureKnownHosts(), false, 5);

        try (ScpFileStorage storage = new ScpFileStorage(config)) {
            storage.store(contentOf("key-auth"), "globelog/2026/07/13/c.jpg");

            String loaded = new String(
                    storage.load("globelog/2026/07/13/c.jpg").readAllBytes(), StandardCharsets.UTF_8);
            assertThat(loaded).isEqualTo("key-auth");
        }
    }

    @Test
    void unknownHostIsRejectedWhenStrictHostKeyCheckingEnabled() throws IOException {
        Path emptyKnownHosts = Files.createTempFile("scp-test-known-hosts-empty", "");

        FileStorageProperties.Scp config = new FileStorageProperties.Scp(
                "127.0.0.1", sshServer.getPort(), USERNAME,
                FileStorageProperties.Scp.AuthMethod.PASSWORD,
                PASSWORD, null, null,
                serverRoot.toString(), emptyKnownHosts.toString(), true, 5);

        assertThatThrownBy(() -> new ScpFileStorage(config))
                .isInstanceOf(IllegalStateException.class);
    }

    private static FileStorageProperties.Scp passwordConfig(String knownHostsPath) {
        return new FileStorageProperties.Scp(
                "127.0.0.1", sshServer.getPort(), USERNAME,
                FileStorageProperties.Scp.AuthMethod.PASSWORD,
                PASSWORD, null, null,
                serverRoot.toString(), knownHostsPath, false, 5);
    }

    // strict-host-key-checking=false 시나리오에서만 쓰는, 검증되지 않는(존재하지 않는) known_hosts 경로.
    private static String insecureKnownHosts() {
        return sharedTempDir.resolve("unused-known-hosts").toString();
    }

    private static boolean samePublicKey(PublicKey a, PublicKey b) {
        return a.equals(b);
    }

    private static ByteArrayInputStream contentOf(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
