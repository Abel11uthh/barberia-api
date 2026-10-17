package com.barberia.api.shared.error;

public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) { super(message); }
}
