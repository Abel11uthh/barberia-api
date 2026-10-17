package com.barberia.api.accounts.repository;

import com.barberia.api.accounts.entity.AppointmentClientEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentClientEventRepository extends JpaRepository<AppointmentClientEvent, UUID> { }
