package com.barberia.api.payments.dto;
import com.barberia.api.shared.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
public record ChargeRequest(@NotNull PaymentMethod method) { }
