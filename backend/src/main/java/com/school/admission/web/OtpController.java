package com.school.admission.web;

import com.school.admission.otp.OtpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/otp")
@Tag(name="OTP verification", description="Guardian email and phone OTP verification")
public class OtpController {
    private final OtpService otp;
    public OtpController(OtpService otp){this.otp=otp;}

    @PostMapping("/request") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    public Map<String,Object> request(@RequestBody Map<String,Object> body){return otp.request(Long.valueOf(String.valueOf(body.get("studentId"))),String.valueOf(body.get("channel")));}

    @PostMapping("/verify") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE')")
    public Map<String,Object> verify(@RequestBody Map<String,Object> body){return otp.verify(String.valueOf(body.get("challengeId")),String.valueOf(body.get("otp")));}

    @GetMapping("/status/{studentId}") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','TEACHER','ACCOUNTS')")
    public Map<String,Object> status(@PathVariable Long studentId){return otp.status(studentId);}
}
