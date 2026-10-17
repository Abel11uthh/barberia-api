package com.barberia.api.shared.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException() { super("Recurso no encontrado."); }
}
