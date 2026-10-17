package com.barberia.api.appointments.service;
import com.barberia.api.shared.error.BusinessConflictException;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
final class AgendaConflicts {
    private AgendaConflicts() { }
    static void flush(Runnable write) {
        try { write.run(); }
        catch (DataIntegrityViolationException error) {
            for (Throwable cause=error; cause!=null; cause=cause.getCause()) {
                if (cause instanceof SQLException sql && ("23P01".equals(sql.getSQLState()) || "23505".equals(sql.getSQLState())))
                    throw new BusinessConflictException("El horario no está disponible.");
            }
            throw error;
        }
    }
}
