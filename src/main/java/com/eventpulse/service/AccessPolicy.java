package com.eventpulse.service;

import com.eventpulse.domain.Event;
import com.eventpulse.exception.ForbiddenOperationException;
import com.eventpulse.security.AppUserPrincipal;
import org.springframework.stereotype.Component;

/** Single place for the "admins manage everything, organizers manage their own events" rule. */
@Component
public class AccessPolicy {

    public boolean canManage(Event event, AppUserPrincipal caller) {
        return caller != null
                && (caller.isAdmin() || event.getOrganizer().getId().equals(caller.id()));
    }

    public void assertCanManage(Event event, AppUserPrincipal caller) {
        if (!canManage(event, caller)) {
            throw new ForbiddenOperationException();
        }
    }
}
