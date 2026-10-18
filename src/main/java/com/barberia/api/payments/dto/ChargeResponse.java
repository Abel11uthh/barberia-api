package com.barberia.api.payments.dto;
import com.barberia.api.payments.entity.AppointmentCharge;
import com.barberia.api.shared.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ChargeResponse(UUID id, UUID appointmentId, UUID branchId, UUID actorId,
        BigDecimal amount, String currency, PaymentMethod method, Instant paidAt) {
    public static ChargeResponse from(AppointmentCharge charge) {
        return new ChargeResponse(charge.getId(),charge.getAppointmentId(),charge.getBranchId(),charge.getActorId(),
                charge.getAmount(),"MXN",charge.getMethod(),charge.getPaidAt());
    }
}
