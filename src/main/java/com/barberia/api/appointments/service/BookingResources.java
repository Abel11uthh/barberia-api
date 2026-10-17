package com.barberia.api.appointments.service;

import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.appointments.entity.BookedService;
import com.barberia.api.barbers.entity.BarberProfile;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.services.entity.*;
import com.barberia.api.services.repository.ServiceRepository;
import com.barberia.api.shared.error.NotFoundException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BookingResources {
    private final BarberRepository barbers;
    private final BranchRepository branches;
    private final AccountRepository accounts;
    private final ServiceRepository services;
    public BookingResources(BarberRepository barbers, BranchRepository branches, AccountRepository accounts, ServiceRepository services) {
        this.barbers=barbers; this.branches=branches; this.accounts=accounts; this.services=services;
    }
    // Called within the booking transaction; shared locks protect catalog/state changes.
    public BarberProfile activeBarber(UUID id) {
        UUID branchId=barbers.findBranchId(id).orElseThrow(NotFoundException::new);
        var branch=branches.findForBooking(branchId).orElseThrow(NotFoundException::new);
        var barber=barbers.findForBooking(id).orElseThrow(NotFoundException::new);
        var account=accounts.findForBooking(barber.getAccount().getId()).orElseThrow(NotFoundException::new);
        if (!branch.isActive() || !barber.isActive() || !account.isActive()) throw new NotFoundException();
        return barber;
    }
    public List<BookedService> services(UUID primary, List<UUID> addons) {
        if (new HashSet<>(addons).size()!=addons.size() || addons.contains(primary)) throw new IllegalArgumentException();
        List<UUID> selected=new ArrayList<>(); selected.add(primary); selected.addAll(addons);
        Map<UUID,CatalogService> catalog=new HashMap<>();
        for(UUID id:selected.stream().sorted().toList()) {
            var service=services.findForBooking(id).orElseThrow(NotFoundException::new);
            if(!service.isActive()) throw new NotFoundException();
            catalog.put(id,service);
        }
        if(catalog.get(primary).getType()!=ServiceType.HAIRCUT
                || addons.stream().anyMatch(id->catalog.get(id).getType()!=ServiceType.ADD_ON)) throw new IllegalArgumentException();
        return selected.stream().map(id->new BookedService(catalog.get(id))).toList();
    }
}
