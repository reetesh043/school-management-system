package com.school.admission.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class OtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${otp.email.mode:console}") private String emailMode;
    @Value("${otp.email.from:no-reply@vidyaone.local}") private String emailFrom;
    @Value("${otp.sms.mode:console}") private String smsMode;
    @Value("${otp.sms.twilio.account-sid:}") private String twilioSid;
    @Value("${otp.sms.twilio.auth-token:}") private String twilioToken;
    @Value("${otp.sms.twilio.from:}") private String twilioFrom;

    public OtpDeliveryService(ObjectProvider<JavaMailSender> mailSender) { this.mailSender = mailSender; }

    public void sendEmail(String to, String code) {
        if (!"smtp".equalsIgnoreCase(emailMode)) {
            log.info("DEV EMAIL OTP for {}: {}", to, code);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new IllegalStateException("SMTP is enabled but mail sender is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom); message.setTo(to); message.setSubject("VidyaOne verification code");
        message.setText("Your VidyaOne verification code is " + code + ". It expires shortly. Do not share this code.");
        sender.send(message);
    }

    public void sendSms(String to, String code) {
        if (!"twilio".equalsIgnoreCase(smsMode)) {
            log.info("DEV PHONE OTP for {}: {}", to, code);
            return;
        }
        if (twilioSid.isBlank() || twilioToken.isBlank() || twilioFrom.isBlank())
            throw new IllegalStateException("Twilio is enabled but TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN or TWILIO_FROM_NUMBER is missing");
        try {
            String form = "To=" + enc(to) + "&From=" + enc(twilioFrom) + "&Body=" + enc("Your VidyaOne verification code is " + code + ". It expires shortly.");
            String auth = Base64.getEncoder().encodeToString((twilioSid + ":" + twilioToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IllegalStateException("SMS provider rejected the message (HTTP " + res.statusCode() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("SMS delivery was interrupted", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) throw ise;
            throw new IllegalStateException("Could not send SMS OTP", e);
        }
    }
    private static String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
