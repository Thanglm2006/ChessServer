package org.example.chessserver.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendVerificationCode(String to, String code) {
        String subject = "Chess App - Verification Code";
        String body = "Dear user,\n\n"
                + "Your verification code is: " + code + "\n\n"
                + "This code is valid for 5 minutes.\n\n"
                + "Best regards,\n"
                + "Chess App Team";
        try {
            sendEmail(to, subject, body);
            log.info("Verification email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}. Error: {}", to, e.getMessage());
            log.info("====== [LOCAL DEVELOPMENT] OTP for {} is {} ======", to, code);
            System.out.println("====== [LOCAL DEVELOPMENT] OTP for " + to + " is " + code + " ======");
        }
    }

    public void sendResetPasswordCode(String to, String code) {
        String subject = "Chess App - Reset Password Code";
        String body = "Dear user,\n\n"
                + "You requested to reset your password. Your verification code is: " + code + "\n\n"
                + "This code is valid for 5 minutes.\n\n"
                + "Best regards,\n"
                + "Chess App Team";
        try {
            sendEmail(to, subject, body);
            log.info("Reset password email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}. Error: {}", to, e.getMessage());
            log.info("====== [LOCAL DEVELOPMENT] Reset Code for {} is {} ======", to, code);
            System.out.println("====== [LOCAL DEVELOPMENT] Reset Code for " + to + " is " + code + " ======");
        }
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom("no-reply@chessapp.com");
        mailSender.send(message);
    }

    @Async
    public void sendTournamentReminder(String toEmail, String username, String tournamentName, String startTimeStr) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("⏰ Nhắc nhở: Giải đấu " + tournamentName + " sắp bắt đầu!");

            String htmlContent = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; background-color: #1c1a17; color: #ffffff;\">"
                    + "<h2 style=\"color: #81b64c; text-align: center;\">ChessPlay Tournament</h2>"
                    + "<p>Xin chào <strong>" + username + "</strong>,</p>"
                    + "<p>Đây là thông báo nhắc nhở rằng giải đấu <strong>" + tournamentName + "</strong> mà bạn đã đăng ký tham gia sẽ chính thức khởi tranh vào lúc:</p>"
                    + "<div style=\"font-size: 18px; font-weight: bold; text-align: center; margin: 20px 0; padding: 15px; background-color: #272421; border-radius: 5px; border-left: 5px solid #81b64c;\">"
                    + startTimeStr
                    + "</div>"
                    + "<p>Vui lòng chuẩn bị sẵn sàng và đăng nhập vào ứng dụng trước giờ bắt đầu để điểm danh tham gia vòng 1.</p>"
                    + "<p style=\"color: #babfc3; font-size: 12px; margin-top: 30px; text-align: center;\">Chúc bạn có những ván đấu tuyệt vời!<br/>ChessPlay Team</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Sent tournament reminder email to user {} ({}) for tournament {}", username, toEmail, tournamentName);
        } catch (Exception e) {
            log.error("Failed to send email to {} for tournament {}", toEmail, tournamentName, e);
        }
    }
}
