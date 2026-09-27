package com.school.admission.web;

import com.school.admission.domain.CommunicationLog;
import com.school.admission.service.CommunicationService;
import com.school.admission.web.dto.Dtos.MessageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications/{id}/messages")
@Tag(name = "Applications", description = "Communication log for an application")
class MessageController {

    private final CommunicationService communication;

    MessageController(CommunicationService communication) {
        this.communication = communication;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<MessageResponse> list(@PathVariable Long id) {
        return communication.forApplication(id).stream()
                .map(m -> new MessageResponse(m.getChannel(), m.getTemplateCode(), m.getStatus(), m.getCreatedAt()))
                .toList();
    }
}
