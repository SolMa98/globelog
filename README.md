# Globelog

지구본을 통해 자신의 여행 기록을 남기고, 다른 사람의 여행을 피드로 구경하는 개인용 여행 기록/소셜 앱입니다.

## 주요 기능

- **3D 지구본**: 방문한 국가/지역을 지구본 위에서 시각적으로 탐색, 국가별 2D 상세 지도와 인스타그램 스토리 형태의 여행 슬라이드
- **여행 기록 관리**: 국가/지역/기간/사진과 함께 여행 등록, 공개범위(전체공개/친구공개/비공개) 설정. 국가는 전세계 236개국이 기본 오픈(지역은 선택 사항)이며, 관리자가 특정 국가/지역을 예외적으로 차단할 수 있음(차단해도 기존에 남긴 여행 기록은 계속 노출됨)
- **소셜 피드**: 팔로우 기반 피드, 프로필 페이지, 팔로우/언팔로우
- **로그인**
  - 이메일/비밀번호 가입 + 이메일 인증
  - Google / Naver / Kakao 간편 로그인
  - 실명 본인인증(PortOne, 다날 채널) — 1인당 계정 1개만 생성 가능하도록 가입 시점에 검증
  - 2차 인증(TOTP 인증 앱 / 이메일 코드), 계정별로 켜고 끌 수 있음
- **관리자 백오피스**: 국가/지역/여행 마스터 데이터 관리(사용 여부 토글 포함), 계정 관리(최상위 관리자/모더레이터 권한 분리), 통계 대시보드(전체 사용자 합산 — 공개범위와 무관하게 집계되며, 개인별 통계는 아직 없음)
- 라이트/다크 모드 (지구본·피드 화면은 의도적으로 다크 고정)

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

계정명/비밀번호가 다르다면 `src/main/resources/application.properties`의 `spring.datasource.*`를 맞춰 수정하세요.

### 2. 실행

```bash
./gradlew bootRun
```

기본적으로 `http://localhost:8080`에서 서비스가 뜹니다. 첫 기동 시 다음이 자동으로 준비됩니다:
- 관리자 계정 생성(계정: `admin` / 비밀번호: `changeme123`, `/admin/login`에서 로그인 후 반드시 변경하세요)
- 전세계 236개국 국가 마스터 데이터 자동 등록(`countries-ref.json` 기반, 이미 등록된 국가는 건드리지 않고 없는 것만 채움 — 재기동해도 안전)

### 3. (선택) 외부 연동 환경변수

아래 값들이 없어도 앱은 정상적으로 기동됩니다 — 값이 없으면 해당 기능만 개별적으로 실패합니다(예: 소셜 로그인 버튼을 눌러도 provider가 거절, 인증 메일 발송 실패 로그만 남음).

| 환경변수 | 용도 | 발급처 |
|---|---|---|
| `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` | 회원가입 인증 메일, 이메일 2차 인증 코드 발송 | Google 계정에서 2단계 인증 활성화 후 "앱 비밀번호" 발급 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google 간편 로그인 | [Google Cloud Console](https://console.cloud.google.com/) → OAuth 클라이언트(웹 애플리케이션), 리디렉션 URI: `{base-url}/login/oauth2/code/google` |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 간편 로그인 | [네이버 개발자센터](https://developers.naver.com/) → 네이버 로그인 API, 리디렉션 URI: `{base-url}/login/oauth2/code/naver` |
| `KAKAO_CLIENT_ID` | 카카오 간편 로그인 | [카카오 개발자센터](https://developers.kakao.com/) → 카카오 로그인 활성화 + 이메일 동의항목, 리디렉션 URI: `{base-url}/login/oauth2/code/kakao` |
| `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_API_SECRET` | 회원가입 시 실명 본인인증(1인 1계정 검증) | [PortOne 콘솔](https://admin.portone.io) → 다날 본인인증 채널 연동. 실서비스 전환에는 PG 심사(사업자등록 등)가 필요할 수 있음 |
| `APP_CRYPTO_SECRET_KEY` | TOTP 시크릿 등 민감정보 암호화 키 | 운영에서는 반드시 별도 값으로 교체(기본값 그대로 쓰지 말 것) |
| `APP_BASE_URL` | 인증 메일 링크, OAuth 콜백 등에 쓰이는 서비스 base URL | 배포 도메인으로 설정(기본값 `http://localhost:8080`) |

## 프로젝트 구조

```
src/main/java/kr/co/dh/globelog/
├── admin/       관리자 백오피스(국가/지역/여행/계정/통계)
├── api/         공개 API(지구본/지도/검색/피드)
├── domain/      JPA 엔티티·레포지토리
├── file/        이미지 업로드
├── identity/    실명 본인인증(PortOne)
├── mail/        메일 발송
├── mytrip/      셀프서비스 여행 CRUD(일반 사용자)
├── profile/     프로필/팔로우/소셜 가입 온보딩
└── security/    인증/인가(로그인, OAuth2, 2차 인증, 세션)
```

## 참고

- Flyway 등 마이그레이션 도구 없이 `ddl-auto=update`로 운용하는 사이드 프로젝트 규모입니다. 스키마 변경 시 기존 데이터에 대한 마이그레이션은 수동으로 처리합니다.
- 개인정보(이메일, 본인인증 DI 등) 처리 방식은 각 도메인 클래스의 주석에 근거와 함께 설명돼 있습니다.
- 이 저장소를 이어받아 개발 중인 로컬 DB는 프로젝트 리네임 이전에 만들어져 스키마/계정명이 아직 `side`/`side_app`입니다 — 운영 데이터가 걸려 있어 별도로 마이그레이션하지 않았습니다. 위 SQL(`globelog`/`globelog_app`)은 새로 환경을 준비할 때 기준이 되는 이름이며, `application.properties`도 그 환경에 맞게 새로 설정하면 됩니다.
