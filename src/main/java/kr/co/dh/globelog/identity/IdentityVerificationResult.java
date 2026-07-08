package kr.co.dh.globelog.identity;

/**
 * 본인인증 성공 결과. DI 원문은 여기까지도 절대 담지 않는다 — 되돌릴 수 없는 해시만 보관.
 */
public record IdentityVerificationResult(String diHash) {
}
