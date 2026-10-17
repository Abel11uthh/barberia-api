package com.barberia.api.auth.bootstrap;

import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.accounts.service.DuplicateEmailException;
import com.barberia.api.auth.dto.RegisterRequest;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {
    private final AccountService accounts;
    private final Environment environment;
    private final Validator validator;

    public AdminInitializer(AccountService accounts, Environment environment, Validator validator) {
        this.accounts = accounts;
        this.environment = environment;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var request = new RegisterRequest(value("name"), value("phone"), value("email"), value("password"));
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("La inicialización administrativa requiere nombre, teléfono, correo y contraseña válidos en el entorno.");
        }
        try {
            accounts.bootstrapAdministrator(request);
        } catch (DuplicateEmailException ignored) {
            // Otra instancia pudo crear la cuenta entre la consulta y la inserción.
            // La cuenta existente se conserva sin elevar privilegios ni cambiar su contraseña.
        }
    }

    private String value(String name) { return environment.getProperty("app.bootstrap-admin." + name); }
}
