package com.school.admission.service;

import com.school.admission.domain.*;
import com.school.admission.engine.TransitionRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seat integrity lives here. hold() locks the class row (SELECT ... FOR UPDATE), so every seat decision for a
 * class is serialised and the count-then-insert cannot race, even with two committee members clicking at once.
 */
@Service
public class SeatService {

    private static final List<String> ACTIVE = List.of(SeatAllocation.HELD, SeatAllocation.CONFIRMED);

    private final ClassConfigRepository classes;
    private final SeatAllocationRepository seats;

    public SeatService(ClassConfigRepository classes, SeatAllocationRepository seats) {
        this.classes = classes;
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public int seatsLeft(Long classConfigId) {
        ClassConfig c = classes.findById(classConfigId).orElseThrow();
        return remaining(c);
    }

    @Transactional
    public void hold(Long applicationId, Long classConfigId, int holdDays) {
        ClassConfig c = classes.findByIdForUpdate(classConfigId).orElseThrow();
        SeatAllocation existing = seats.findByApplicationId(applicationId).orElse(null);
        boolean alreadyActive = existing != null && ACTIVE.contains(existing.getStatus());
        if (alreadyActive) {
            return;
        }
        if (remaining(c) <= 0) {
            throw new TransitionRejectedException(List.of(
                    "The last seat in " + c.getDisplayName() + " was just taken. You can move this application to the waitlist."));
        }
        SeatAllocation seat = existing != null ? existing : new SeatAllocation();
        seat.setClassConfigId(classConfigId);
        seat.setApplicationId(applicationId);
        seat.setStatus(SeatAllocation.HELD);
        seat.setHeldUntil(Instant.now().plus(holdDays, ChronoUnit.DAYS));
        seat.setUpdatedAt(Instant.now());
        seats.save(seat);
    }

    @Transactional
    public void release(Long applicationId) {
        seats.findByApplicationId(applicationId).ifPresent(seat -> {
            if (ACTIVE.contains(seat.getStatus())) {
                seat.setStatus(SeatAllocation.RELEASED);
                seat.setHeldUntil(null);
                seat.setUpdatedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void confirm(Long applicationId) {
        seats.findByApplicationId(applicationId).ifPresent(seat -> {
            seat.setStatus(SeatAllocation.CONFIRMED);
            seat.setHeldUntil(null);
            seat.setUpdatedAt(Instant.now());
        });
    }

    private int remaining(ClassConfig c) {
        long used = seats.countByClassConfigIdAndStatusIn(c.getId(), ACTIVE);
        return (int) (c.getSeatsTotal() - c.getSeatsPreFilled() - used);
    }
}
