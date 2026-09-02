##Added Spring Security Dependencies and Configurations

override SecurityFilterChain filterChain(HttpSecurity http){
    http.csrf().disable() // Disable CSRF protection
        .authorizeHttpRequests((auth) -> auth
            .anyRequest.permitAll()
        ) // Allow all requests without authentication
        .formLogin((form) -> form.disable()) // Disable form login
        .httpBasic(httpBasic -> httpBasic.disable()); // Disable HTTP Basic authentication
}
-> to provide a basic security configuration that disables CSRF protection, allows all requests without authentication, and disables form login and HTTP Basic authentication.
->This configuration is useful for applications that do not require user authentication or for development purposes where security is not a concern.


