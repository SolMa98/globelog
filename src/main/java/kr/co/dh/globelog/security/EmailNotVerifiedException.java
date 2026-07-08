package kr.co.dh.globelog.security;

import org.springframework.security.authentication.DisabledException;

/**
 * 이메일 인증을 마치지 않은 계정으로 로그인을 시도할 때 던진다. 관리자가 계정 자체를
 * 비활성화한 경우(DisabledException)와 로그인 실패 화면에서 다른 안내 문구를 보여주기
 * 위해 별도 타입으로 분리했다 — LoginFailureHandler 참고.
 */
public class EmailNotVerifiedException extends DisabledException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
