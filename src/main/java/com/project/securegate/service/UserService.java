package com.project.securegate.service;

import com.project.securegate.entity.User;
import com.project.securegate.entity.UserLoginDto;
import com.project.securegate.exception.UserNotFoundException;
import com.project.securegate.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    public String login(UserLoginDto user) throws UserNotFoundException {
        // check if user exists in repository
        User existingUser = userRepository.findByEmail(user.getEmail()).orElseThrow(()-> new UserNotFoundException("User not found with email: " + user.getEmail()));

        if (existingUser != null) {
            return "Login successful.";
        } else {
            return "Invalid email or password.";
        }
    }
    public void registerUser(User user) {

        // save to repository;
        userRepository.save(user);
    }
}
