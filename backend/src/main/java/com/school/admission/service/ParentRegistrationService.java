package com.school.admission.service;

import com.school.admission.otp.OtpService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ParentRegistrationService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final OtpService otp;

    public ParentRegistrationService(JdbcTemplate jdbc, PasswordEncoder encoder, OtpService otp) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.otp = otp;
    }

    @Transactional
    public Map<String,Object> requestOtp(String admissionNoRaw, String dobRaw, String channel) {
        String admissionNo = clean(admissionNoRaw).toUpperCase(Locale.ROOT);
        if (admissionNo.isBlank()) throw new IllegalArgumentException("Enter the student's admission number");
        if (clean(dobRaw).isBlank()) throw new IllegalArgumentException("Enter the student's date of birth");
        Date dob;
        try { dob = Date.valueOf(dobRaw); }
        catch (Exception ex) { throw new IllegalArgumentException("Date of birth must be a valid date"); }

        Map<String,Object> student = jdbc.query("""
            select s.id,s.full_name,s.guardian_name,s.guardian_user_id,c.display_name
              from student s join class_config c on c.id=s.class_config_id
             where upper(s.admission_no)=? and s.dob=? and s.status='ACTIVE'
            """, (rs,i)->{
                Map<String,Object> m=new LinkedHashMap<>();
                m.put("id",rs.getLong("id")); m.put("studentName",rs.getString("full_name"));
                m.put("guardianName",rs.getString("guardian_name")); m.put("className",rs.getString("display_name"));
                m.put("guardianUserId",rs.getObject("guardian_user_id")); return m;
            }, admissionNo, dob).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Student details did not match our records"));

        if (student.get("guardianUserId") != null)
            throw new IllegalArgumentException("A parent/guardian account is already linked to this student. Please sign in or contact the school.");

        Map<String,Object> challenge = new LinkedHashMap<>(otp.request(((Number)student.get("id")).longValue(), channel));
        challenge.put("studentName", student.get("studentName"));
        challenge.put("guardianName", student.get("guardianName"));
        challenge.put("className", student.get("className"));
        return challenge;
    }

    @Transactional
    public Map<String,Object> complete(String challengeId, String code, String usernameRaw, String password) {
        String username = clean(usernameRaw).toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._@+-]{4,50}"))
            throw new IllegalArgumentException("Username must be 4-50 characters and may use letters, numbers, dot, _, @, + or -");
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*"))
            throw new IllegalArgumentException("Password must be at least 8 characters and include a letter and a number");
        if (!jdbc.queryForList("select id from app_user where lower(username)=?", username).isEmpty())
            throw new IllegalArgumentException("That username is already in use");

        Map<String,Object> verified = otp.verify(challengeId, code);
        Long studentId = ((Number)verified.get("studentId")).longValue();
        Map<String,Object> student = jdbc.query("select guardian_name,guardian_email,guardian_phone,guardian_user_id from student where id=?",
                (rs,i)->Map.<String,Object>of(
                        "guardianName", rs.getString("guardian_name") == null ? "Parent / Guardian" : rs.getString("guardian_name"),
                        "email", rs.getString("guardian_email") == null ? "" : rs.getString("guardian_email"),
                        "phone", rs.getString("guardian_phone") == null ? "" : rs.getString("guardian_phone"),
                        "linked", rs.getObject("guardian_user_id") == null ? "" : String.valueOf(rs.getLong("guardian_user_id"))
                ), studentId).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Student not found"));
        if (!String.valueOf(student.get("linked")).isBlank())
            throw new IllegalArgumentException("A parent/guardian account was already linked while you were registering. Please sign in or contact the school.");

        jdbc.update("insert into app_user(username,password_hash,full_name,email,phone,enabled) values(?,?,?,?,?,true)",
                username, encoder.encode(password), student.get("guardianName"), blankToNull(student.get("email")), blankToNull(student.get("phone")));
        Long userId = jdbc.queryForObject("select id from app_user where username=?", Long.class, username);
        jdbc.update("insert into user_role(user_id,role) values(?,'GUARDIAN')", userId);
        jdbc.update("update student set guardian_user_id=? where id=? and guardian_user_id is null", userId, studentId);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registered", true); out.put("username", username); out.put("fullName", student.get("guardianName")); out.put("studentId", studentId);
        return out;
    }

    private static String clean(Object o){ return o==null?"":String.valueOf(o).trim(); }
    private static String blankToNull(Object o){ String s=clean(o); return s.isBlank()?null:s; }
}
