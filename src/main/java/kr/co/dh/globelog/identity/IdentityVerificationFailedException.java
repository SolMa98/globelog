package kr.co.dh.globelog.identity;

/** 본인인증이 완료되지 않았거나(READY/FAILED), PortOne 조회 자체가 실패했을 때. */
public class IdentityVerificationFailedException extends RuntimeException {

    public IdentityVerificationFailedException(String message) {
        super(message);
    }

    public IdentityVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
