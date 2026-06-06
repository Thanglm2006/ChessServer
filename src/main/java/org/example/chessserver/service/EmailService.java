package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
}
