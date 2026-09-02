package com.project.securegate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secure-gate")
public class SecureGateController {
    @GetMapping("/status")
    public String getStatus() {
        return "Secure Gate is running.";
    }
}
