package kr.co.dh.globelog.identity;

/** 이미 다른 계정이 같은 실명 본인(DI)으로 가입돼 있을 때. */
public class DuplicateIdentityException extends RuntimeException {

    public DuplicateIdentityException(String message) {
        super(message);
    }
}
