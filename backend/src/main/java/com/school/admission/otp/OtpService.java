package com.school.admission.otp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OtpService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final OtpDeliveryService delivery;
    private final SecureRandom random = new SecureRandom();

    @Value("${otp.expiry-minutes:5}") private long expiryMinutes;
    @Value("${otp.resend-seconds:60}") private long resendSeconds;
    @Value("${otp.max-attempts:5}") private int maxAttempts;
    @Value("${otp.expose-dev-code:false}") private boolean exposeDevCode;
    @Value("${otp.email.mode:console}") private String emailMode;
    @Value("${otp.sms.mode:console}") private String smsMode;

    public OtpService(JdbcTemplate jdbc, PasswordEncoder encoder, OtpDeliveryService delivery) {
        this.jdbc=jdbc; this.encoder=encoder; this.delivery=delivery;
    }

    @Transactional
    public Map<String,Object> request(Long studentId, String channelRaw) {
        String channel = normalizeChannel(channelRaw);
        Map<String,Object> student = studentContact(studentId);
        String destination = "EMAIL".equals(channel) ? clean(student.get("email")) : clean(student.get("phone"));
        if (destination.isBlank()) throw new IllegalArgumentException("EMAIL".equals(channel) ? "Guardian email is not available" : "Guardian phone is not available");
        if ("EMAIL".equals(channel) && !destination.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Guardian email is not valid");
        if ("PHONE".equals(channel)) destination = normalizePhone(destination);

        List<Timestamp> cooldown = jdbc.query("select resend_available_at from otp_challenge where student_id=? and channel=? and verified_at is null order by created_at desc limit 1", (rs,i)->rs.getTimestamp(1), studentId, channel);
        if (!cooldown.isEmpty() && cooldown.get(0).toInstant().isAfter(Instant.now())) {
            long seconds = Math.max(1, ChronoUnit.SECONDS.between(Instant.now(), cooldown.get(0).toInstant()));
            throw new IllegalArgumentException("Please wait " + seconds + " seconds before requesting another OTP");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String id = UUID.randomUUID().toString(); Instant now=Instant.now();
        jdbc.update("insert into otp_challenge(id,student_id,channel,destination,code_hash,purpose,attempts,max_attempts,expires_at,resend_available_at,created_at) values(?,?,?,?,?,'CONTACT_VERIFICATION',0,?,?,?,?,?)",
                id,studentId,channel,destination,encoder.encode(code),maxAttempts,Timestamp.from(now.plus(expiryMinutes,ChronoUnit.MINUTES)),Timestamp.from(now.plus(resendSeconds,ChronoUnit.SECONDS)),Timestamp.from(now));
        if ("EMAIL".equals(channel)) delivery.sendEmail(destination,code); else delivery.sendSms(destination,code);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("challengeId",id); out.put("channel",channel); out.put("destination",mask(channel,destination));
        out.put("expiresInSeconds",expiryMinutes*60); out.put("resendAfterSeconds",resendSeconds);
        if (exposeDevCode && (("EMAIL".equals(channel)&&!"smtp".equalsIgnoreCase(emailMode)) || ("PHONE".equals(channel)&&!"twilio".equalsIgnoreCase(smsMode)))) out.put("devOtp",code);
        return out;
    }

    @Transactional
    public Map<String,Object> verify(String challengeId, String otp) {
        if (otp == null || !otp.matches("\\d{6}")) throw new IllegalArgumentException("Enter the 6-digit OTP");
        Map<String,Object> c = jdbc.query("select id,student_id,channel,destination,code_hash,attempts,max_attempts,expires_at,verified_at from otp_challenge where id=?", (rs,i)->{
            Map<String,Object> m=new HashMap<>();m.put("studentId",rs.getLong("student_id"));m.put("channel",rs.getString("channel"));m.put("hash",rs.getString("code_hash"));m.put("attempts",rs.getInt("attempts"));m.put("maxAttempts",rs.getInt("max_attempts"));m.put("expires",rs.getTimestamp("expires_at"));m.put("verified",rs.getTimestamp("verified_at"));return m;
        }, challengeId).stream().findFirst().orElseThrow(()->new NoSuchElementException("OTP request not found"));
        if(c.get("verified")!=null) throw new IllegalArgumentException("This OTP has already been used");
        if(((Timestamp)c.get("expires")).toInstant().isBefore(Instant.now())) throw new IllegalArgumentException("OTP has expired. Request a new code.");
        int attempts=(Integer)c.get("attempts"), max=(Integer)c.get("maxAttempts");
        if(attempts>=max) throw new IllegalArgumentException("Too many incorrect attempts. Request a new code.");
        if(!encoder.matches(otp,String.valueOf(c.get("hash")))){
            jdbc.update("update otp_challenge set attempts=attempts+1 where id=?",challengeId);
            int remaining=Math.max(0,max-attempts-1); throw new IllegalArgumentException("Incorrect OTP. " + remaining + " attempt" + (remaining==1?"":"s") + " remaining.");
        }
        Instant verified=Instant.now(); Long studentId=(Long)c.get("studentId"); String channel=String.valueOf(c.get("channel"));
        jdbc.update("update otp_challenge set verified_at=? where id=?",Timestamp.from(verified),challengeId);
        jdbc.update("update student set " + ("EMAIL".equals(channel)?"guardian_email_verified_at":"guardian_phone_verified_at") + "=? where id=?",Timestamp.from(verified),studentId);
        return status(studentId);
    }

    public Map<String,Object> status(Long studentId){
        return jdbc.query("select guardian_email,guardian_phone,guardian_email_verified_at,guardian_phone_verified_at from student where id=?",(rs,i)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("studentId",studentId);m.put("email",rs.getString(1));m.put("phone",rs.getString(2));m.put("emailVerified",rs.getTimestamp(3)!=null);m.put("phoneVerified",rs.getTimestamp(4)!=null);m.put("emailVerifiedAt",rs.getTimestamp(3));m.put("phoneVerifiedAt",rs.getTimestamp(4));return m;
        },studentId).stream().findFirst().orElseThrow(()->new NoSuchElementException("Student not found: "+studentId));
    }

    private Map<String,Object> studentContact(Long studentId){ return jdbc.query("select guardian_email,guardian_phone from student where id=?",(rs,i)->Map.<String,Object>of("email",Objects.toString(rs.getString(1),""),"phone",Objects.toString(rs.getString(2),"")),studentId).stream().findFirst().orElseThrow(()->new NoSuchElementException("Student not found: "+studentId)); }
    private String normalizeChannel(String c){String x=clean(c).toUpperCase(Locale.ROOT);if(!Set.of("EMAIL","PHONE").contains(x))throw new IllegalArgumentException("Channel must be EMAIL or PHONE");return x;}
    private String normalizePhone(String p){String x=p.replaceAll("[\\s()-]","");if(x.startsWith("00"))x="+"+x.substring(2);if(x.matches("^[6-9]\\d{9}$"))x="+91"+x;if(x.matches("^91[6-9]\\d{9}$"))x="+"+x;if(!x.matches("^\\+[1-9]\\d{7,14}$"))throw new IllegalArgumentException("Phone number must be a valid mobile number, e.g. 9876543210 or +919876543210");return x;}
    private static String clean(Object o){return o==null?"":String.valueOf(o).trim();}
    private static String mask(String channel,String v){if("EMAIL".equals(channel)){int at=v.indexOf('@');if(at<=1)return "***"+v.substring(Math.max(0,at));return v.substring(0,1)+"***"+v.substring(at);}return v.length()<=4?"****":"****"+v.substring(v.length()-4);}
}
