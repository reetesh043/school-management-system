package com.school.admission.web;

import com.school.admission.domain.InteractionSlot;
import com.school.admission.service.SchedulingService;
import com.school.admission.web.dto.Dtos.BookSlotRequest;
import com.school.admission.web.dto.Dtos.SlotResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/interaction-slots")
@Tag(name = "Scheduling", description = "Interaction, campus tour and entrance-test slots")
class SchedulingController {

    private final SchedulingService scheduling;

    SchedulingController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<SlotResponse> upcoming() {
        return scheduling.upcoming().stream().map(SchedulingController::toResponse).toList();
    }

    @PostMapping("/{slotId}/bookings")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> book(@PathVariable Long slotId, @Valid @RequestBody BookSlotRequest req) {
        scheduling.book(slotId, req.applicationId());
        return ResponseEntity.status(201).build();
    }

    private static SlotResponse toResponse(InteractionSlot s) {
        return new SlotResponse(s.getId(), s.getKind(), s.getStartsAt(), s.getEndsAt(), s.getCapacity() - s.getBooked());
    }
}
