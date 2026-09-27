package com.school.admission.service;

import com.school.admission.domain.InteractionBooking;
import com.school.admission.domain.InteractionBookingRepository;
import com.school.admission.domain.InteractionSlot;
import com.school.admission.domain.InteractionSlotRepository;
import com.school.admission.support.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Slot booking. The slot row is locked while booking, so two guardians cannot both take the last opening. */
@Service
public class SchedulingService {

    private final InteractionSlotRepository slots;
    private final InteractionBookingRepository bookings;

    public SchedulingService(InteractionSlotRepository slots, InteractionBookingRepository bookings) {
        this.slots = slots;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public List<InteractionSlot> upcoming() {
        return slots.findByStartsAtAfterOrderByStartsAt(Instant.now());
    }

    @Transactional
    public InteractionBooking book(Long slotId, Long applicationId) {
        InteractionSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Slot not found"));
        if (slot.getBooked() >= slot.getCapacity()) {
            throw new BusinessRuleException("This slot is full. Please choose another one.");
        }
        slot.setBooked(slot.getBooked() + 1);
        InteractionBooking booking = new InteractionBooking();
        booking.setSlotId(slotId);
        booking.setApplicationId(applicationId);
        return bookings.save(booking);
    }
}
