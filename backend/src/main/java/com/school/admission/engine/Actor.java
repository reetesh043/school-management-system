package com.school.admission.engine;

import java.util.Collection;
import java.util.Set;

/** Who is asking for a transition: a guardian, a staff member, or the system (webhooks and jobs). */
public record Actor(String id, Type type, Set<String> roles) {

    public enum Type { GUARDIAN, STAFF, SYSTEM }

    public static final Actor SYSTEM = new Actor("system", Type.SYSTEM, Set.of("SYSTEM"));

    public boolean hasAnyRole(Collection<String> allowed) {
        return roles.stream().anyMatch(allowed::contains);
    }
}
