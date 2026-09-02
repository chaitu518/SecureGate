package com.project.securegate.service;

import com.project.securegate.entity.User;
import com.project.securegate.repository.UserRepository;
import com.project.securegate.security.AppUserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {


    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found with email: " + email
                        )
                );

        return new AppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getHashPassword(),
                user.isEnabled(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
