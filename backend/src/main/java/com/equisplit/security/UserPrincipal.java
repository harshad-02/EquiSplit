package com.equisplit.security;

import com.equisplit.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String email;
    private final String password;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String name, String email, String password, String role, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.authorities = authorities;
    }

    public static UserPrincipal create(User user) {
        String roleName = user.getRole() != null ? user.getRole().toUpperCase() : "USER";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName);

        return new UserPrincipal(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                Collections.singletonList(authority)
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPrincipal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
