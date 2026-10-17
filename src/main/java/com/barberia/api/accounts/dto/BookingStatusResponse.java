package com.barberia.api.accounts.dto;

public record BookingStatusResponse(boolean bookingBlocked, int consecutiveAppointmentIncidents,
                                    String warning) { }
