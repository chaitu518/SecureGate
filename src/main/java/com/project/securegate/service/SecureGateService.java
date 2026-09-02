package com.project.securegate.service;


import com.project.securegate.entity.RequestRegisterUserDto;
import com.project.securegate.entity.User;
import com.project.securegate.entity.UserLoginDto;
import com.project.securegate.entity.VerificationToken;
import com.project.securegate.exception.UserNotFoundException;
import com.project.securegate.repository.UserRepository;
import com.project.securegate.repository.VerificationTokenRepository;
import com.project.securegate.utils.VerificationTokenUtil;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SecureGateService {

    private UserRepository userRepository;
    private VerificationTokenRepository verificationTokenRepository;
    private PasswordEncoder passwordEncoder;

    public SecureGateService(UserRepository userRepository, VerificationTokenRepository verificationTokenRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(UserLoginDto user) throws UserNotFoundException {
        // check if user exists in repository
        User existingUser = userRepository.findByEmail(user.getEmail()).orElseThrow(() -> new UserNotFoundException("User not found with email: " + user.getEmail()));

        if (!existingUser.isEnabled()) {
            throw new UserNotFoundException("User not verified with email: " + user.getEmail());
        }
        if(!passwordEncoder.matches(user.getPassword(),existingUser.getHashPassword())) {
            throw new UserNotFoundException("Invalid password for email: " + user.getEmail());
        }
        return "Login successful.";
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
