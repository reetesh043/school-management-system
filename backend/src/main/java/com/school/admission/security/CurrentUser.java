package com.school.admission.security;

import com.school.admission.domain.AppUser;
import com.school.admission.domain.AppUserRepository;
import com.school.admission.engine.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns the Spring Security principal into the things the services need. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser user(Authentication auth) {
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    public Set<String> roles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }

    public boolean hasRole(Authentication auth, String role) {
        return roles(auth).contains(role);
    }

    /** A guardian who is not also staff. Guardians only ever see their own applications. */
    public boolean isGuardianOnly(Authentication auth) {
        Set<String> roles = roles(auth);
        return roles.contains("GUARDIAN") && roles.stream().allMatch("GUARDIAN"::equals);
    }

    public Actor actor(Authentication auth) {
        return new Actor(auth.getName(), isGuardianOnly(auth) ? Actor.Type.GUARDIAN : Actor.Type.STAFF, roles(auth));
    }
}
