package kr.co.dh.globelog.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 인증 메일/이메일 OTP 발송 전용 서비스. 발신 계정은 Gmail SMTP(앱 비밀번호)를
 * 전제로 하며, application.properties의 spring.mail.* 값이 채워져 있어야 실제 발송됨.
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String baseUrl;

    public MailService(JavaMailSender mailSender, @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
    }

    public void sendVerificationEmail(String to, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Globelog] 이메일 인증을 완료해주세요");
        message.setText(
                "아래 링크를 클릭하면 이메일 인증이 완료됩니다.\n\n"
                        + verifyUrl
                        + "\n\n이 링크는 24시간 동안 유효합니다.");
        mailSender.send(message);
    }

    public void sendOtpEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Globelog] 로그인 인증 코드");
        message.setText("로그인 인증 코드: " + code + "\n\n이 코드는 5분간 유효합니다.");
        mailSender.send(message);
    }
}
