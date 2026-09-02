package com.project.securegate.controller;

import com.project.securegate.entity.User;
import com.project.securegate.entity.UserLoginDto;
import com.project.securegate.exception.UserNotFoundException;
import com.project.securegate.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/secure-gate")
public class SecureGateController {

    private UserService userService;

    public SecureGateController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody User user) {
        //System.out.println(user);
        userService.registerUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserLoginDto userLoginDto) throws UserNotFoundException {
        String response = userService.login(userLoginDto);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/status")
    public String getStatus() {
        return "Secure Gate is running.";
    }

}
