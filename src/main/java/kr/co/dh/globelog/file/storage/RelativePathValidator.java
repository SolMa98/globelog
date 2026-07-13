package kr.co.dh.globelog.file.storage;

/**
 * FileStorage 구현체들이 공통으로 쓰는 상대 경로 검증. "../"로 베이스 디렉터리를 벗어나거나
 * 절대 경로("/..." 또는 "C:\...")로 시작하는 값을 걸러낸다 — 저장 시점(우리가 만든 경로라
 * 항상 안전)뿐 아니라, 조회 시점(요청 URL에서 그대로 넘어온 값이라 신뢰할 수 없음)에도
 * 반드시 거쳐야 한다.
 */
final class RelativePathValidator {

    private RelativePathValidator() {
    }

    static void validate(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("경로가 비어 있습니다.");
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\") || relativePath.contains(":")) {
            throw new SecurityException("절대 경로는 허용되지 않습니다: " + relativePath);
        }
        for (String segment : relativePath.split("[/\\\\]")) {
            if (segment.equals("..")) {
                throw new SecurityException("상위 디렉터리 접근은 허용되지 않습니다: " + relativePath);
            }
        }
    }
}
