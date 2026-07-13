package kr.co.dh.globelog.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kr.co.dh.globelog.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings({"unchecked", "rawtypes"})
class IdentityVerificationServiceTest {

    private static final String HASH_KEY = "test-crypto-secret";

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec uriSpec;
    private RestClient.RequestHeadersSpec headersSpec;
    private RestClient.ResponseSpec responseSpec;
    private UserRepository userRepository;
    private IdentityVerificationService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(String.class))).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        userRepository = mock(UserRepository.class);
        service = new IdentityVerificationService(userRepository, builder, "api-secret", HASH_KEY);
    }

    @Test
    void blankVerificationIdFailsWithoutExternalCall() {
        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(IdentityVerificationFailedException.class);
        assertThatThrownBy(() -> service.verify("  "))
                .isInstanceOf(IdentityVerificationFailedException.class);
    }

    @Test
    void nonVerifiedStatusFails() {
        stubResponse("{\"status\":\"PENDING\"}");

        assertThatThrownBy(() -> service.verify("iv-1"))
                .isInstanceOf(IdentityVerificationFailedException.class);
    }

    @Test
    void alreadyRegisteredDiThrowsDuplicateIdentity() {
        stubResponse("{\"status\":\"VERIFIED\",\"verifiedCustomer\":{\"di\":\"same-person-di\"}}");
        when(userRepository.existsByIdentityDiHash(any())).thenReturn(true);

        assertThatThrownBy(() -> service.verify("iv-1"))
                .isInstanceOf(DuplicateIdentityException.class);
    }

    @Test
    void successfulVerificationReturnsHashNotRawDi() {
        stubResponse("{\"status\":\"VERIFIED\",\"verifiedCustomer\":{\"di\":\"same-person-di\"}}");
        when(userRepository.existsByIdentityDiHash(any())).thenReturn(false);

        IdentityVerificationResult result = service.verify("iv-1");

        assertThat(result.diHash()).isNotEqualTo("same-person-di");
        // EncryptedStringConverter와 같은 마스터 키를 그대로 재사용한 게 아니라, 컨텍스트를
        // 섞어 파생한 키로 해시했는지 확인 — 이 값과 달라야 키 재사용 회귀가 없다는 뜻.
        assertThat(result.diHash()).isNotEqualTo(rawHmac(HASH_KEY, "same-person-di"));
    }

    private void stubResponse(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        when(responseSpec.body(JsonNode.class)).thenReturn(node);
    }

    private static String rawHmac(String key, String value) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}