package com.project.securegate.controller;

import com.project.securegate.entity.RegisteredUserDto;
import com.project.securegate.entity.RequestRegisterUserDto;
import com.project.securegate.entity.User;
import com.project.securegate.entity.UserLoginDto;
import com.project.securegate.exception.UserNotFoundException;
import com.project.securegate.service.SecureGateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/secure-gate")
public class SecureGateController {

    private SecureGateService secureGateService;

    public SecureGateController(SecureGateService secureGateService) {
        this.secureGateService = secureGateService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisteredUserDto> registerUser(@RequestBody RequestRegisterUserDto requestRegisterUserDto) {

        User user1 = secureGateService.registerUser(requestRegisterUserDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisteredUserDto(user1.getId(), user1.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserLoginDto userLoginDto) throws UserNotFoundException {
        String response = secureGateService.login(userLoginDto);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/verification-token")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        secureGateService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully.");
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('USER')")
    public String getStatus() {
        //System.out.println("entered into getStatus");
        return "Secure Gate is running.";
    }

}
