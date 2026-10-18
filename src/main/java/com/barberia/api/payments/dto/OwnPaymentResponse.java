package com.barberia.api.payments.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record OwnPaymentResponse(boolean paid, BigDecimal amount, String currency, Instant paidAt) { }
