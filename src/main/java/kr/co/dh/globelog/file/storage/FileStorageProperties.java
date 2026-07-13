package kr.co.dh.globelog.file.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게시글 이미지 저장소 설정. LOCAL(프로젝트 외부의 로컬 디렉터리) / SCP(SSH·SFTP로 외부 서버)
 * 두 방식을 application.properties의 app.storage.mode 값으로 전환한다.
 * 모든 기본값은 application.properties의 ${ENV_VAR:default} 자리표시자로 채워지므로
 * 여기서는 별도 @DefaultValue를 두지 않는다.
 */
@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(
        StorageMode mode,
        String projectName,
        Local local,
        Scp scp) {

    public enum StorageMode { LOCAL, SCP }

    public record Local(String baseDir) {
    }

    public record Scp(
            String host,
            int port,
            String username,
            AuthMethod authMethod,
            String password,
            String privateKeyPath,
            String privateKeyPassphrase,
            String remoteDir,
            String knownHostsPath,
            boolean strictHostKeyChecking,
            int connectTimeoutSeconds) {

        public enum AuthMethod { PASSWORD, PRIVATE_KEY }
    }
}
