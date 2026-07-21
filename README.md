# Globelog

지구본을 통해 자신의 여행 기록을 남기고, 다른 사람의 여행을 피드로 구경하는 개인용 여행 기록/소셜 앱입니다.

## 주요 기능

- **3D 지구본**: 방문한 국가/지역을 지구본 위에서 시각적으로 탐색, 국가별 2D 상세 지도와 인스타그램 스토리 형태의 여행 슬라이드
- **여행 기록 관리**: 국가/지역/기간/사진과 함께 여행 등록, 공개범위(전체공개/친구공개/비공개) 설정. 국가는 전세계 236개국이 기본 오픈(지역은 선택 사항)이며, 관리자가 특정 국가/지역을 예외적으로 차단할 수 있음(차단해도 기존에 남긴 여행 기록은 계속 노출됨)
- **소셜 피드**: 팔로우 기반 피드, 프로필 페이지, 팔로우/언팔로우
- **채팅**: 회원 간 실시간 채팅(WebSocket/STOMP) — 1:1 DM(중복 방지), 그룹 채팅(멤버 누구나 초대 가능), 나와의 채팅(개인 메모용, 1인당 1개). 이미지/문서 첨부 가능(첨부파일은 3개월 후 자동 삭제, 여행·게시글 사진은 보관기간 없음). 메시지는 본인 것만 수정/삭제 가능(soft-delete). 새 메시지는 해당 방을 보고 있지 않은 멤버에게만 브라우저/OS 푸시 알림(Web Push)으로 전달
- **로그인**
  - 이메일/비밀번호 가입 + 이메일 인증
  - Google / Naver / Kakao 간편 로그인
  - 실명 본인인증(PortOne, 다날 채널) — 1인당 계정 1개만 생성 가능하도록 가입 시점에 검증
  - 2차 인증(TOTP 인증 앱 / 이메일 코드), 계정별로 켜고 끌 수 있음
- **관리자 백오피스**: 국가/지역/여행 마스터 데이터 관리(사용 여부 토글 포함), 계정 관리(최상위 관리자/모더레이터 권한 분리), 통계 대시보드(전체 사용자 합산 — 공개범위와 무관하게 집계되며, 개인별 통계는 아직 없음), IP 화이트리스트(선택, 기본 비활성)로 접근 제한
- 라이트/다크 모드 (지구본·피드·채팅 화면은 의도적으로 다크 고정, 채팅은 모바일에서 풀스크린 앱처럼 표시)

## 기술 스택

- Java 21, Spring Boot 4.1 (Spring MVC, Spring Security, Spring Data JPA, Spring Session JDBC)
- Thymeleaf, 순수 JavaScript(프레임워크 없음), MapLibre GL JS
- MariaDB
- Gradle

## 시작하기

### 1. 데이터베이스 준비

MariaDB에 데이터베이스와 계정을 만듭니다(**테이블은** `ddl-auto=update`로 앱 기동 시 자동 생성됩니다 — 데이터베이스 자체와 접속 계정만 미리 만들어두면 됩니다).

```sql
CREATE DATABASE globelog CHARACTER SET utf8mb4;
CREATE USER 'globelog_app'@'localhost' IDENTIFIED BY 'globelog_app_pw';
GRANT ALL PRIVILEGES ON globelog.* TO 'globelog_app'@'localhost';
```

계정명/비밀번호가 다르다면 `DB_USERNAME`/`DB_PASSWORD` 환경변수로 덮어쓰세요(기본값은 위 SQL과 동일하게 `side_app`/`side_app_pw`로 맞춰져 있음).

### 2. 필수 환경변수

`APP_CRYPTO_SECRET_KEY`는 기본값이 없습니다 — 값이 없으면(빈 문자열이면) `EncryptedStringConverter`가 기동 시점에 예외를 던지고 앱이 뜨지 않습니다. TOTP 시크릿 암호화와 본인인증 DI 해시에 쓰이는 마스터 키이므로, 안전하지 않은 기본값으로 조용히 동작하는 상황을 막기 위한 의도적인 제약입니다. 이 키가 바뀌면 그 전에 암호화해둔 값(TOTP 시크릿 등)은 복호화할 수 없게 되므로, 한 번 정한 값은 계속 유지해야 합니다.

**로컬 개발(권장)**: 프로젝트 루트에 `local-secrets.properties` 파일을 만들어두면 서버가 자동으로 읽습니다(`.gitignore`에 등록돼 있어 커밋되지 않고, 매번 셸에 export할 필요도 없음).

```bash
echo "APP_CRYPTO_SECRET_KEY=$(openssl rand -base64 32)" > local-secrets.properties
```

**운영/CI**: 파일 대신 실제 OS 환경변수로 주입합니다.

```bash
export APP_CRYPTO_SECRET_KEY=$(openssl rand -base64 32)
```

### 3. 실행

```bash
./gradlew bootRun
```

기본적으로 `http://localhost:15790`에서 서비스가 뜹니다(`SERVER_PORT` 환경변수로 변경 가능). 첫 기동 시 다음이 자동으로 준비됩니다:
- 관리자 계정 생성(계정: `admin` / 비밀번호: `changeme123`). 이 계정은 `must_change_password` 플래그가 켜진 채로 만들어져서, 최초 로그인 시 `/admin/change-password`로 강제 이동되고 비밀번호를 바꾸기 전까지는 다른 어드민 화면에 들어갈 수 없음(신규 관리자 계정을 추가로 만들 때도 동일하게 적용됨). 배포 시에는 `ADMIN_BOOTSTRAP_PASSWORD` 환경변수로 최초 비밀번호 자체도 바꿀 수 있음 — 단, 이미 계정이 생성된 이후에는 이 값을 바꿔도 기존 비밀번호에 영향 없음
- 전세계 236개국 국가 마스터 데이터 자동 등록(`countries-ref.json` 기반, 이미 등록된 국가는 건드리지 않고 없는 것만 채움 — 재기동해도 안전)

### 4. (선택) 외부 연동 환경변수

아래 값들이 없어도 앱은 정상적으로 기동됩니다 — 값이 없으면 해당 기능만 개별적으로 실패합니다(예: 소셜 로그인 버튼을 눌러도 provider가 거절, 인증 메일 발송 실패 로그만 남음).

| 환경변수 | 용도 | 발급처 |
|---|---|---|
| `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` | 회원가입 인증 메일, 이메일 2차 인증 코드 발송 | Google 계정에서 2단계 인증 활성화 후 "앱 비밀번호" 발급 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google 간편 로그인 | [Google Cloud Console](https://console.cloud.google.com/) → OAuth 클라이언트(웹 애플리케이션), 리디렉션 URI: `{base-url}/login/oauth2/code/google` |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 간편 로그인 | [네이버 개발자센터](https://developers.naver.com/) → 네이버 로그인 API, 리디렉션 URI: `{base-url}/login/oauth2/code/naver` |
| `KAKAO_CLIENT_ID` | 카카오 간편 로그인 | [카카오 개발자센터](https://developers.kakao.com/) → 카카오 로그인 활성화 + 이메일 동의항목, 리디렉션 URI: `{base-url}/login/oauth2/code/kakao` |
| `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_API_SECRET` | 회원가입 시 실명 본인인증(1인 1계정 검증) | [PortOne 콘솔](https://admin.portone.io) → 다날 본인인증 채널 연동. 실서비스 전환에는 PG 심사(사업자등록 등)가 필요할 수 있음 |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | 채팅 새 메시지 브라우저/OS 알림(Web Push) | EC(P-256) 키쌍, base64url 인코딩(패딩 없음). `KeyPairGenerator.getInstance("EC", "BC")`(secp256r1)로 생성 후 `nl.martijndwars.webpush.Utils.encode(...)`로 인코딩. 값이 없으면 알림 발송만 조용히 비활성화됨 |
| `SERVER_PORT` | 서버가 뜨는 포트 | 기본값 `15790` |
| `APP_BASE_URL` | 인증 메일 링크, OAuth 콜백 등에 쓰이는 서비스 base URL | 배포 도메인으로 설정(기본값 `http://localhost:15790`) |
| `SESSION_COOKIE_SECURE` | 세션 쿠키 Secure 속성 | HTTPS로 배포할 때만 `true`로 설정(로컬 HTTP 개발 중에 켜면 로그인이 깨짐, 기본값 `false`) |
| `DB_USERNAME`, `DB_PASSWORD` | MariaDB 접속 계정 | 1단계에서 만든 계정/비밀번호가 기본값과 다르면 설정 |
| `ADMIN_BOOTSTRAP_PASSWORD` | 최초 관리자 계정 비밀번호 | 기본값(`changeme123`) 대신 배포 전에 설정 권장 |
| `ADMIN_IP_WHITELIST_ENABLED` | 관리자 IP 화이트리스트 켜기/끄기 | 기본값 `false`. 켜기 전 `/admin/ip-whitelist`에서 본인 IP를 먼저 등록할 것(아래 "관리자 IP 화이트리스트" 참고) |

## 파일 저장소

여행/게시글 이미지는 프로젝트 디렉터리 밖에 저장됩니다(재배포·git clean 등으로 업로드 파일이 함께 날아가지 않도록). `app.storage.mode`로 두 방식 중 하나를 고릅니다 — 저장뿐 아니라 조회(`/uploads/**`)와 삭제(게시글 삭제 시)도 항상 같은 방식을 따릅니다.

- **LOCAL**(기본값): 서버 로컬 디스크의 `app.storage.local.base-dir`(기본 `${user.home}/globelog-uploads`)에 저장
- **SCP**: SSH(SFTP)로 외부 서버의 `app.storage.scp.remote-dir`에 저장. `known_hosts`에 등록된 호스트만 신뢰하므로(`strict-host-key-checking=true`가 기본값), 연결 전에 `ssh-keyscan -H <host> >> ~/.ssh/known_hosts` 등으로 먼저 등록해야 함. 인증은 비밀번호/개인키 중 선택(`auth-method=PASSWORD|PRIVATE_KEY`)

두 방식 모두 저장 경로는 `{project-name}/{년}/{월}/{일}/{UUID}.{확장자}` 구조로 자동 정리됩니다(예: `globelog/2026/07/13/9697c459-....jpg`).

| 환경변수 | 용도 | 비고 |
|---|---|---|
| `APP_STORAGE_MODE` | `LOCAL` 또는 `SCP` | 기본값 `LOCAL` |
| `APP_STORAGE_PROJECT_NAME` | 저장 경로 최상위 폴더명 | 기본값 `globelog` |
| `APP_STORAGE_LOCAL_DIR` | LOCAL 모드 저장 경로 | 기본값 `${user.home}/globelog-uploads` |
| `APP_STORAGE_SCP_HOST`, `APP_STORAGE_SCP_PORT`, `APP_STORAGE_SCP_USERNAME` | SCP 모드 접속 정보 | 포트 기본값 22 |
| `APP_STORAGE_SCP_AUTH_METHOD` | `PASSWORD` 또는 `PRIVATE_KEY` | 기본값 `PASSWORD` |
| `APP_STORAGE_SCP_PASSWORD` | 비밀번호 인증 시 사용 | `local-secrets.properties`에 두는 걸 권장 |
| `APP_STORAGE_SCP_PRIVATE_KEY_PATH`, `APP_STORAGE_SCP_PRIVATE_KEY_PASSPHRASE` | 개인키 인증 시 사용 | 키 파일 자체는 서버에만 두고 경로만 지정 |
| `APP_STORAGE_SCP_REMOTE_DIR` | 원격 저장 베이스 디렉터리 | 기본값 `/var/globelog/uploads` |
| `APP_STORAGE_SCP_KNOWN_HOSTS_PATH` | known_hosts 파일 경로 | 기본값 `${user.home}/.ssh/known_hosts` |
| `APP_STORAGE_SCP_STRICT_HOST_KEY_CHECKING` | 미등록 호스트 거부 여부 | 기본값 `true`(권장). `false`로 끄면 MITM 방지가 사라지므로 known_hosts를 아직 준비 못 한 임시 상황에서만 사용 |
| `APP_STORAGE_SCP_CONNECT_TIMEOUT_SECONDS` | 연결 타임아웃(초) | 기본값 10 |

## 관리자 IP 화이트리스트

관리자 백오피스(`/admin/**`, 로그인 화면 포함) 접근을 특정 IP/CIDR로 제한할 수 있습니다. 기본은 **비활성**입니다.

- 목록은 DB(`admin_ip_whitelist` 테이블)로 관리하며, `/admin/ip-whitelist`(최상위 관리자 전용) 화면에서 추가/삭제
- 켜고 끄는 스위치(`app.admin.ip-whitelist.enabled`)는 서버 재시작이 필요한 설정값으로 따로 둠 — 화이트리스트를 잘못 등록해 스스로 접근이 막히더라도, 이 값을 `false`로 되돌리고 재시작하면 즉시 복구할 수 있게 하기 위함
- `127.0.0.1`/`::1`(loopback)은 화이트리스트 내용과 무관하게 항상 허용 — 서버에 직접 접속(SSH 등)할 수 있으면 항상 복구 가능
- **켜기 전에 반드시** `/admin/ip-whitelist`에서 본인 IP(또는 대역)를 먼저 등록해두고 나서 `ADMIN_IP_WHITELIST_ENABLED=true`로 켤 것 — 순서를 반대로 하면 원격 서버 기준으로는 스스로 접근이 막힘
- 리버스 프록시(Nginx 등) 뒤에 서버를 두는 경우, 필터는 `request.getRemoteAddr()`를 그대로 사용하므로 `server.forward-headers-strategy`를 설정해서 실제 클라이언트 IP가 전달되게 해야 함 — 프록시가 `X-Forwarded-For`를 검증/덮어쓰지 않으면 헤더 위조로 우회될 수 있으니 주의

## 테스트

```bash
./gradlew test
```

암호화(AES-GCM 롤백/키 분리), 본인인증(중복가입 판정), 여행 소유권 체크, 파일 업로드 시그니처 검증, 관리자 강제 비밀번호 변경, IP 화이트리스트 필터 등 보안에 직접 영향을 주는 로직 위주로 단위 테스트가 있습니다. SCP(SFTP) 저장소는 임베디드 SSH 테스트 서버를 띄워 store/load/delete와 known_hosts 미등록 호스트 거부까지 실제 핸드셰이크로 검증합니다.

## 프로젝트 구조

```
src/main/java/kr/co/dh/globelog/
├── admin/       관리자 백오피스(국가/지역/여행/계정/통계)
├── api/         공개 API(지구본/지도/검색/피드)
├── chat/        실시간 채팅(WebSocket/STOMP) — DM/그룹/개인방, 메시지 CRUD
├── domain/      JPA 엔티티·레포지토리
├── file/        이미지 업로드(storage/ 하위에 LOCAL/SCP 저장소 구현)
├── identity/    실명 본인인증(PortOne)
├── mail/        메일 발송
├── mytrip/      셀프서비스 여행 CRUD(일반 사용자)
├── profile/     프로필/팔로우/소셜 가입 온보딩
├── push/        Web Push 구독 관리·알림 발송
└── security/    인증/인가(로그인, OAuth2, 2차 인증, 세션)
```

## 참고

- Flyway 등 마이그레이션 도구 없이 `ddl-auto=update`로 운용하는 사이드 프로젝트 규모입니다. 스키마 변경 시 기존 데이터에 대한 마이그레이션은 수동으로 처리합니다.
- 개인정보(이메일, 본인인증 DI 등) 처리 방식은 각 도메인 클래스의 주석에 근거와 함께 설명돼 있습니다.
- 이 저장소를 이어받아 개발 중인 로컬 DB는 프로젝트 리네임 이전에 만들어져 스키마/계정명이 아직 `side`/`side_app`입니다 — 운영 데이터가 걸려 있어 별도로 마이그레이션하지 않았습니다. 위 SQL(`globelog`/`globelog_app`)은 새로 환경을 준비할 때 기준이 되는 이름이며, `application.properties`도 그 환경에 맞게 새로 설정하면 됩니다.
