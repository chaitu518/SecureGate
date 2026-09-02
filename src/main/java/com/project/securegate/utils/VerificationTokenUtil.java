package com.project.securegate.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class VerificationTokenUtil{
    public static String generateVerificationCode() {
        // Generate a random 6-digit verification code
        String token = UUID.randomUUID().toString();
        return token;
    }
    public static boolean isValid(Instant createdAt) {
        Instant expirationTime = createdAt.plus(1, ChronoUnit.DAYS);
        return Instant.now().isBefore(expirationTime);
    }

}
