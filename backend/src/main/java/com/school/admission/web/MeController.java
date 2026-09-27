package com.school.admission.web;

import com.school.admission.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Lets the console ask "who am I", since the JWT-free Basic-auth flow has no token to decode client-side. */
@RestController
@Tag(name = "Applications", description = "The signed-in user's own id, name and roles")
class MeController {

    public record Me(Long id, String username, String fullName, Set<String> roles) { }

    private final CurrentUser currentUser;

    MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/me")
    @PreAuthorize("isAuthenticated()")
    Me me(Authentication auth) {
        var user = currentUser.user(auth);
        return new Me(user.getId(), user.getUsername(), user.getFullName(), currentUser.roles(auth));
    }
}
