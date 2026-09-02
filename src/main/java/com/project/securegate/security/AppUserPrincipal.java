package com.project.securegate.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class AppUserPrincipal extends User {

    private final Long id;

    public AppUserPrincipal(Long id, String email, String password, boolean enabled,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, password, enabled, true, true, true, authorities);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
