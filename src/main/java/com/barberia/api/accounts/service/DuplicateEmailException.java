package com.barberia.api.accounts.service;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() { super("El correo ya está registrado."); }
}
