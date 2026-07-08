package kr.co.dh.globelog.security.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RFC 6238(TOTP) 직접 구현 — 구글 OTP(Google Authenticator) 등 표준 인증기 앱과 호환된다.
 * 알고리즘이 단순하고 표준이 고정돼 있어(HMAC-SHA1, 30초 스텝, 6자리) 별도 라이브러리
 * 없이 javax.crypto.Mac만으로 충분하다고 판단해 직접 작성했다.
 */
@Service
public class TotpService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int ALLOWED_STEP_DRIFT = 1; // 앞뒤 30초씩 허용(시계 오차 대비)

    public String generateSecret() {
        byte[] randomBytes = new byte[20];
        new SecureRandom().nextBytes(randomBytes);
        return Base32.encode(randomBytes);
    }

    public String buildOtpAuthUri(String secret, String accountEmail, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    public boolean verifyCode(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long currentStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (int drift = -ALLOWED_STEP_DRIFT; drift <= ALLOWED_STEP_DRIFT; drift++) {
            if (code.equals(generateCode(base32Secret, currentStep + drift))) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(String base32Secret, long timeStep) {
        byte[] key = Base32.decode(base32Secret);
        byte[] counter = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counter[i] = (byte) (timeStep & 0xFF);
            timeStep >>= 8;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counter);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 코드 생성 실패", e);
        }
    }
}
