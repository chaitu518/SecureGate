package com.project.securegate.service;


import com.project.securegate.entity.*;
import com.project.securegate.exception.UserNotFoundException;
import com.project.securegate.repository.UserRepository;
import com.project.securegate.repository.VerificationTokenRepository;
import com.project.securegate.utils.VerificationTokenUtil;

import com.project.securegate.security.AppUserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SecureGateService {

    private UserRepository userRepository;
    private VerificationTokenRepository verificationTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;

    public SecureGateService(UserRepository userRepository, VerificationTokenRepository verificationTokenRepository, PasswordEncoder passwordEncoder,  JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public String login(UserLoginDto user) throws UserNotFoundException {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            user.getPassword()
                    )
            );
        } catch (DisabledException e) {
            throw new UserNotFoundException("User not verified with email: " + user.getEmail());
        } catch (BadCredentialsException e) {
            throw new UserNotFoundException("Invalid email or password");
        }

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        // generate JWT token
        RegisteredUserDto registeredUserDto = new RegisteredUserDto();
        registeredUserDto.setEmail(principal.getUsername());
        registeredUserDto.setId(principal.getId());
        return jwtService.generateToken(registeredUserDto);
    }

    public User registerUser(RequestRegisterUserDto registerUser) {

        Optional<User> existingUser = userRepository.findByEmail(registerUser.getEmail());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("User with email " + registerUser.getEmail() + " already exists.");
        }
        // save to repository;
        User user = new User();
        user.setEmail(registerUser.getEmail());
        user.setHashPassword(passwordEncoder.encode(registerUser.getPassword()));
        User user1 = userRepository.save(user);
        //VerificationToken login
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user1);
        verificationToken.setToken(VerificationTokenUtil.generateVerificationCode());
        verificationToken.setCreatedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);
        //send email
        System.out.println("http://localhost:8080/api/secure-gate/verification-token?token=" + verificationToken.getToken());
        return user1;
    }

    public void verifyEmail(String token) {

        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }
        if(!VerificationTokenUtil.isValid(verificationToken.getCreatedAt())) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);

        verificationToken.setUsed(true);

        userRepository.save(user);
        verificationTokenRepository.save(verificationToken);
    }
}
