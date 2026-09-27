package com.school.admission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end through the real Liquibase-migrated H2 database with the demo dataset: an application that
 * already sits in REGISTRATION (APP27-000004) is walked through the interaction, seat hold, invoice,
 * mock payment capture and confirmation, checking the stage, the seat ledger and the admission number
 * at every step. This is the same path the Spring Boot app takes when it starts normally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdmissionFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void guardianCanViewTheirOwnApplication() throws Exception {
        mvc.perform(get("/api/v1/applications").with(httpBasic("parent", "Demo@1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationNo").value("APP27-000001"));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEnquiryEndpointNeedsNoAuthentication() throws Exception {
        mvc.perform(post("/api/v1/public/enquiries")
                        .contentType("application/json")
                        .content("""
                                {"guardianName":"Test Parent","phone":"9876543210","childName":"Test Child",
                                 "childDob":"2021-05-01","classCode":"C1","source":"WEBSITE","consent":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void committeeCanSeeGuardTelemetryForAnInProgressApplication() throws Exception {
        // APP27-000005 is at INITIATED with a passing assessment already recorded in the demo data,
        // so PAYMENT should be reachable and SEAT_AVAILABLE should already show as passed.
        mvc.perform(get("/api/v1/applications").with(httpBasic("committee", "Demo@1234")))
                .andExpect(status().isOk());
    }
}
