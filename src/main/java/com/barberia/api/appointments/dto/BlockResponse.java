package com.barberia.api.appointments.dto;
import com.barberia.api.appointments.entity.BarberBlock;
import java.time.Instant;
import java.util.UUID;
public record BlockResponse(UUID id, UUID barberId, Instant startsAt, Instant endsAt, String reason,
                            String status, UUID createdBy, Instant createdAt, UUID cancelledBy, Instant cancelledAt) {
    public static BlockResponse from(BarberBlock b) {
        return new BlockResponse(b.getId(),b.getBarberId(),b.getStartsAt(),b.getEndsAt(),b.getReason(),
                b.getStatus(),b.getCreatedBy(),b.getCreatedAt(),b.getCancelledBy(),b.getCancelledAt());
    }
}
