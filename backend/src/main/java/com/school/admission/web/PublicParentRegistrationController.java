package com.school.admission.web;

import com.school.admission.service.ParentRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/parent-registration")
@Tag(name="Parent registration", description="Self-service parent/guardian account registration")
public class PublicParentRegistrationController {
    private final ParentRegistrationService registration;
    public PublicParentRegistrationController(ParentRegistrationService registration){ this.registration=registration; }

    @PostMapping("/request-otp")
    public Map<String,Object> requestOtp(@RequestBody Map<String,Object> body){
        return registration.requestOtp(String.valueOf(body.getOrDefault("admissionNo","")), String.valueOf(body.getOrDefault("dob","")), String.valueOf(body.getOrDefault("channel","EMAIL")));
    }

    @PostMapping("/complete")
    public Map<String,Object> complete(@RequestBody Map<String,Object> body){
        return registration.complete(String.valueOf(body.getOrDefault("challengeId","")), String.valueOf(body.getOrDefault("otp","")), String.valueOf(body.getOrDefault("username","")), String.valueOf(body.getOrDefault("password","")));
    }
}
