package com.project.securegate.service;

import com.project.securegate.entity.User;
import com.project.securegate.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    public User registerUser(User user) {

        // save to repository;
        return  userRepository.save(user);
    }
}
