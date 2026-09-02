package com.project.securegate.controller;

import com.project.securegate.entity.User;
import com.project.securegate.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/secure-gate")
public class SecureGateController {

    private UserService userService;

    public SecureGateController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        //System.out.println(user);
        return userService.registerUser(user);
    }
    @GetMapping("/status")
    public String getStatus() {
        return "Secure Gate is running.";
    }

}
