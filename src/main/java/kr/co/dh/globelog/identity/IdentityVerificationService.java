package kr.co.dh.globelog.identity;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * PortOne(다날 채널) 본인인증 결과를 조회해서 "1인 1계정" 여부를 판정한다.
 *
 * CI는 아예 수집하지 않는다. DI("자사 내 중복가입 확인" 용도로 설계된 값)만 쓰되, 원문은
 * 절대 저장하지 않고 HMAC-SHA256으로 되돌릴 수 없게 해시한 값만 User.identityDiHash에
 * 남긴다 — 조회(중복 체크)엔 결정적 해시로 충분하고 원문을 보관할 이유가 없다(데이터 최소수집).
 * 이름/생년월일/전화번호 등 응답에 포함된 다른 개인정보는 이 메서드 밖으로 절대 내보내지 않는다.
 */
@Service
public class IdentityVerificationService {

    private final RestClient restClient;
    private final UserRepository userRepository;
    private final String apiSecret;
    private final String hashKey;

    public IdentityVerificationService(
            UserRepository userRepository,
            @Value("${portone.api-secret}") String apiSecret,
            @Value("${app.crypto.secret-key}") String hashKey) {
        this.restClient = RestClient.builder().baseUrl("https://api.portone.io").build();
        this.userRepository = userRepository;
        this.apiSecret = apiSecret;
        this.hashKey = hashKey;
    }

    /**
     * 본인인증 완료 여부와 중복 가입 여부를 확인한다. 문제가 없을 때만 결과를 반환하고,
     * 그 외에는 전부 예외로 처리한다 — 호출부(회원가입/소셜 온보딩)는 계정을 만들기 전에
     * 이 메서드를 반드시 통과시켜야 한다.
     */
    public IdentityVerificationResult verify(String identityVerificationId) {
        if (identityVerificationId == null || identityVerificationId.isBlank()) {
            throw new IdentityVerificationFailedException("본인인증을 완료해주세요.");
        }

        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/identity-verifications/{id}", identityVerificationId)
                    .header("Authorization", "PortOne " + apiSecret)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new IdentityVerificationFailedException(
                    "본인인증 확인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        }

        if (body == null || !"VERIFIED".equals(body.path("status").asString(null))) {
            throw new IdentityVerificationFailedException("본인인증이 완료되지 않았습니다.");
        }

        String di = body.path("verifiedCustomer").path("di").asString(null);
        if (di == null || di.isBlank()) {
            throw new IdentityVerificationFailedException("본인인증 결과에서 필요한 정보를 받지 못했습니다.");
        }

        String diHash = hash(di);
        if (userRepository.existsByIdentityDiHash(diHash)) {
            throw new DuplicateIdentityException("이미 본인인증을 마친 계정이 있습니다. 기존 계정으로 로그인해주세요.");
        }

        return new IdentityVerificationResult(diHash);
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("DI 해시 계산 실패", e);
        }
    }
}
