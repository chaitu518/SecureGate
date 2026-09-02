# SecureGate

SecureGate is a Spring Boot–based authentication and authorization service implementing **user registration, email verification, password hashing, database-backed authentication, JWT-based authentication, and role-based authorization**.

The project was built incrementally to understand how authentication works internally with Spring Security rather than relying only on built-in login mechanisms.

---

## 1. Objective

The main objective of SecureGate is to implement a secure authentication flow where:

1. A user registers with an email and password.
2. The password is securely hashed before being stored.
3. A verification token is generated for the user's email.
4. The user verifies their email through the verification link.
5. Only verified users can log in.
6. Spring Security authenticates the user's credentials.
7. A JWT is generated after successful authentication.
8. The client sends the JWT with subsequent requests.
9. A custom JWT filter validates the token.
10. The authenticated user's role is placed into the Spring Security context.
11. Protected endpoints are authorized using Spring Security and `@PreAuthorize`.

---

# 2. Technology Stack

* **Java 21**
* **Spring Boot 4.1.1**
* **Spring Security**
* **Spring Data JPA**
* **Hibernate ORM**
* **SQL Database**
* **JJWT 0.12.6**
* **Gradle**
* **Tomcat**

---

# 3. High-Level Architecture

```text
                    ┌──────────────────────┐
                    │       Client         │
                    └──────────┬───────────┘
                               │
                               │ HTTP Request
                               ▼
                    ┌──────────────────────┐
                    │  Spring Security     │
                    │   Filter Chain       │
                    └──────────┬───────────┘
                               │
                     ┌─────────▼─────────┐
                     │ JWT Authentication │
                     │      Filter        │
                     └─────────┬─────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ DispatcherServlet    │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │     Controller       │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │       Service        │
                    │   Business Logic     │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │      Repository      │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │     SQL Database     │
                    └──────────────────────┘
```

---

# 4. Implementation Journey

The implementation was done in multiple stages.

---

## Stage 1 — Database Integration

The first step was connecting the application to a SQL database and introducing JPA entities and repositories.

### User Entity

The `User` entity stores information such as:

* User ID
* Email
* Hashed password
* Role
* Enabled/verified status

The database table was explicitly named `users`.

### Why `users` instead of `user`?

`USER` can be a reserved keyword in SQL/H2.

Therefore:

```java
@Entity
@Table(name = "users")
public class User {
    ...
}
```

This prevents SQL table-name conflicts.

---

# 5. Password Security

Passwords should never be stored as plain text.

Initially, authentication logic could directly compare the supplied password with the stored password. This was changed to use Spring Security's password encoding mechanism.

We configured:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

During registration:

```text
Raw Password
      │
      ▼
PasswordEncoder
      │
      ▼
BCrypt Hash
      │
      ▼
Database
```

The database stores the BCrypt hash rather than the original password.

### Why BCrypt?

BCrypt is designed specifically for password hashing and is intentionally computationally expensive, making brute-force attacks more difficult.

---

# 6. User Registration

The registration process is handled by `SecureGateService`.

### Registration flow

```text
Client
  │
  │ POST /register
  ▼
Controller
  │
  ▼
SecureGateService
  │
  ├── Check whether email already exists
  │
  ├── Hash password using PasswordEncoder
  │
  ├── Create User
  │
  ├── Save User
  │
  ├── Generate verification token
  │
  └── Save VerificationToken
  │
  ▼
Response
```

### Important changes

The password is encoded before persistence:

```java
user.setHashPassword(
    passwordEncoder.encode(registerUser.getPassword())
);
```

The email is also checked before creating a new user:

```java
Optional<User> existingUser =
    userRepository.findByEmail(registerUser.getEmail());
```

This prevents duplicate registrations for the same email.

---

# 7. Email Verification

A separate `VerificationToken` entity was introduced.

It contains information such as:

```text
VerificationToken
│
├── id
├── token
├── user
├── createdAt
├── expiresAt
└── used
```

The token is associated with the user:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

---

## Why a separate VerificationToken entity?

Email verification is a separate responsibility from the `User` entity.

Instead of putting verification-token information directly inside `User`, a separate entity allows us to manage:

* Token value
* Token ownership
* Token creation time
* Token expiration
* Token usage

This also keeps the user model cleaner.

---

# 8. Verification Token Generation

After successful registration:

```text
User Created
    │
    ▼
Verification Token Generated
    │
    ▼
Token Associated With User
    │
    ▼
Token Stored In Database
```

The generated verification URL is:

```text
http://localhost:8080/api/secure-gate/verification-token?token=<TOKEN>
```

The important concept is that the token is stored server-side and linked to the registered user.

---

# 9. Email Verification Flow

The verification endpoint receives the token.

```text
User clicks verification link
          │
          ▼
Verification Controller
          │
          ▼
SecureGateService
          │
          ▼
Find VerificationToken
          │
          ├── Token doesn't exist?
          │       └── Reject
          │
          ├── Token already used?
          │       └── Reject
          │
          ├── Token expired?
          │       └── Reject
          │
          ▼
       Get User
          │
          ▼
   Enable User Account
          │
          ▼
     Mark Token Used
```

The user is enabled after successful verification:

```java
user.setEnabled(true);
```

The token is also marked as used:

```java
verificationToken.setUsed(true);
```

### Why `used` instead of `deleted`?

A verification token represents an event that occurred.

Keeping the token and marking it as used provides an explicit state:

```text
USED = false
```

means it can still be used.

```text
USED = true
```

means it has already been consumed.

---

# 10. Token Expiration

Token validity is based on its creation/expiration time rather than maintaining a separate `expired` flag.

The important distinction is:

```text
createdAt
    +
expiration duration
    =
expiration time
```

For example:

```java
createdAt.plus(1, ChronoUnit.DAYS)
```

represents a one-day validity period.

This avoids maintaining redundant state such as:

```text
expired = true/false
```

because expiration can be calculated from the timestamp.

---

# 11. Spring Security Integration

After implementing registration and verification, authentication was moved into Spring Security.

The important components introduced were:

```text
UserDetailsService
       │
       ▼
DaoAuthenticationProvider
       │
       ▼
AuthenticationManager
       │
       ▼
Authentication
```

---

# 12. CustomUserDetailsService

`CustomUserDetailsService` connects Spring Security with our database.

Spring Security does not automatically know how our `User` entity is stored.

Therefore, we implemented:

```java
@Service
public class CustomUserDetailsService
        implements UserDetailsService
```

The main method is:

```java
loadUserByUsername(String email)
```

Although Spring Security calls the parameter `username`, in our application the username is the user's **email**.

The service:

```text
Email
 │
 ▼
UserRepository
 │
 ▼
Database
 │
 ▼
User Entity
 │
 ▼
Spring Security UserDetails
```

---

# 13. UserDetails

The database `User` entity and Spring Security's `UserDetails` are different concepts.

Our application entity:

```text
User
```

represents our application's user.

Spring Security requires:

```text
UserDetails
```

to perform authentication.

Therefore, `CustomUserDetailsService` acts as the adapter between our application model and Spring Security.

Example:

```java
return User.withUsername(user.getEmail())
        .password(user.getHashPassword())
        .authorities(user.getRole())
        .disabled(!user.isEnabled())
        .build();
```

---

# 14. DaoAuthenticationProvider

We configured:

```java
DaoAuthenticationProvider
```

with:

* `CustomUserDetailsService`
* `PasswordEncoder`

Conceptually:

```text
Login Request
     │
     ▼
AuthenticationManager
     │
     ▼
DaoAuthenticationProvider
     │
     ├── UserDetailsService
     │       │
     │       ▼
     │    Database
     │
     └── PasswordEncoder
             │
             ▼
       Verify Password
```

### Why use DaoAuthenticationProvider?

It gives Spring Security responsibility for credential authentication rather than manually implementing password comparison inside our service.

---

# 15. AuthenticationManager

The application exposes:

```java
@Bean
public AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration)
        throws Exception {
    return configuration.getAuthenticationManager();
}
```

The `AuthenticationManager` becomes the entry point for username/password authentication.

---

# 16. Login Flow

The login process was changed significantly.

Instead of manually doing:

```java
passwordEncoder.matches(...)
```

the service now delegates authentication to Spring Security:

```java
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(
        user.getEmail(),
        user.getPassword()
    )
);
```

The complete flow is:

```text
Client
  │
  │ Email + Password
  ▼
Login Controller
  │
  ▼
SecureGateService
  │
  ▼
AuthenticationManager
  │
  ▼
DaoAuthenticationProvider
  │
  ├── CustomUserDetailsService
  │       │
  │       ▼
  │     Database
  │
  └── PasswordEncoder
          │
          ▼
      Verify Password
          │
          ▼
   Authentication Success
          │
          ▼
      JwtService
          │
          ▼
        JWT
```

This is an important architectural change because **credential authentication is now delegated to Spring Security**.

---

# 17. Verified User Check

Before authenticating, the application checks whether the user has verified their email.

```java
if (!existingUser.isEnabled()) {
    throw new UserNotFoundException(
        "User not verified with email: " + user.getEmail()
    );
}
```

Therefore:

```text
Registered + Not Verified
        │
        ▼
      Login
        │
        ▼
      Rejected
```

Whereas:

```text
Registered + Verified
        │
        ▼
      Login
        │
        ▼
 AuthenticationManager
        │
        ▼
       JWT
```

---

# 18. JWT Authentication

After successful login, `JwtService` generates a JWT.

The JWT contains:

```text
Subject → User Email
userId  → User ID
role    → ROLE_USER
iat     → Issued At
exp     → Expiration
```

Example structure:

```text
JWT
│
├── Subject
│     └── user@example.com
│
├── userId
│     └── 123
│
├── role
│     └── ROLE_USER
│
├── issuedAt
│
└── expiration
```

The token is signed using an HMAC secret.

---

# 19. JWT Secret Configuration

Initially, the JWT secret was generated randomly during application startup.

That creates an important problem:

```text
Application starts
      │
      ▼
New Secret Generated
      │
      ▼
JWT #1 generated
```

After restart:

```text
Application restarts
      │
      ▼
Different Secret Generated
      │
      ▼
JWT #1 can no longer be validated
```

Therefore, the secret was moved into application configuration:

```properties
jwt.secret=...
```

The application reads this value and creates the signing key.

---

# 20. Constructor Injection for JWT Configuration

A problem was encountered when the secret was injected using field injection while the key was initialized immediately.

Incorrect pattern:

```java
@Value("${jwt.secret}")
private String secret;

private final SecretKey secretKey =
    Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
```

The problem is Java initializes fields before Spring performs field injection.

Therefore `secret` can still be `null` when `secretKey` is initialized.

The implementation was changed to constructor injection:

```java
@Component
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.secretKey =
            Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
            );
    }
}
```

This ensures the secret is available before the key is constructed.

---

# 21. JWT Validation

For every protected request, the JWT must be validated.

The `JwtService` uses the signing key to parse and verify the token.

```java
Jwts.parser()
    .verifyWith(secretKey)
    .build()
    .parseSignedClaims(token);
```

This validates the token signature and claims such as expiration.

---

# 22. JwtAuthenticationFilter

A custom:

```java
OncePerRequestFilter
```

was implemented.

Its responsibility is to inspect every incoming request for a JWT.

Expected header:

```text
Authorization: Bearer <JWT>
```

Flow:

```text
HTTP Request
     │
     ▼
Authorization Header
     │
     ▼
Does it start with "Bearer "?
     │
     ├── NO ──► Continue Request
     │
     ▼
Extract JWT
     │
     ▼
Validate JWT
     │
     ├── Invalid ──► Continue without authentication
     │
     ▼
Extract username
     │
     ▼
Extract role
     │
     ▼
Create Authentication
     │
     ▼
SecurityContextHolder
     │
     ▼
Continue Request
```

---

# 23. Why `OncePerRequestFilter`?

The JWT authentication logic belongs at the servlet filter level because authentication must happen **before the request reaches protected controller methods**.

The request lifecycle is approximately:

```text
Client
  ↓
Tomcat
  ↓
Servlet Filters
  ↓
Spring Security Filter Chain
  ↓
JwtAuthenticationFilter
  ↓
DispatcherServlet
  ↓
Interceptor
  ↓
Controller
  ↓
Service
  ↓
Repository
```

Therefore the JWT filter is executed before the controller.

---

# 24. Registering the JWT Filter

The JWT filter is registered using:

```java
.addFilterBefore(
    jwtAuthenticationFilter,
    UsernamePasswordAuthenticationFilter.class
)
```

This places our JWT authentication logic at the appropriate point in Spring Security's filter chain.

---

# 25. SecurityContextHolder

After successful JWT validation, an authentication object is created:

```java
UsernamePasswordAuthenticationToken authentication =
    new UsernamePasswordAuthenticationToken(
        username,
        null,
        Collections.singletonList(
            new SimpleGrantedAuthority(role)
        )
    );
```

Then:

```java
SecurityContextHolder
    .getContext()
    .setAuthentication(authentication);
```

The `SecurityContextHolder` now contains the authentication for the current request.

Conceptually:

```text
JWT
 │
 ▼
JwtAuthenticationFilter
 │
 ▼
Authentication
 │
 ▼
SecurityContextHolder
 │
 ├── Principal → User Email
 │
 └── Authorities → ROLE_USER
```

---

# 26. Stateless Authentication

The application uses:

```java
.sessionManagement(session ->
    session.sessionCreationPolicy(
        SessionCreationPolicy.STATELESS
    )
)
```

This means Spring Security does not maintain authentication using an HTTP session.

Instead:

```text
Request 1 → JWT
Request 2 → JWT
Request 3 → JWT
Request 4 → JWT
```

Each request carries the token.

This is appropriate for a JWT-based REST API.

---

# 27. CSRF Configuration

During development, protected endpoints initially returned `403 Forbidden`.

The reason was Spring Security's CSRF protection.

For a stateless REST API using JWT in the `Authorization` header, CSRF protection is generally not required in this architecture.

Therefore:

```java
.csrf(csrf -> csrf.disable())
```

was configured.

### Important distinction

```java
permitAll()
```

and:

```java
csrf.disable()
```

solve different problems.

`permitAll()` controls **authorization**.

`csrf.disable()` controls **CSRF protection**.

---

# 28. Disabling Form Login and Basic Authentication

The application does not use Spring Security's default HTML login page or HTTP Basic authentication.

Therefore:

```java
.formLogin(form -> form.disable())
.httpBasic(httpBasic -> httpBasic.disable())
```

were added.

This does **not** disable:

* `AuthenticationManager`
* `UserDetailsService`
* `DaoAuthenticationProvider`
* `PasswordEncoder`
* JWT authentication
* Spring Security authorization

It only removes the authentication mechanisms we are not using.

---

# 29. Public and Protected Endpoints

The following endpoints are public:

```text
POST /api/secure-gate/register
POST /api/secure-gate/login
GET  /api/secure-gate/verification-token
```

They are configured using:

```java
.requestMatchers(
    "/api/secure-gate/register",
    "/api/secure-gate/login",
    "/api/secure-gate/verification-token"
).permitAll()
```

Everything else requires authentication:

```java
.anyRequest().authenticated()
```

Therefore:

```text
/register              → Public
/login                 → Public
/verification-token    → Public

/status                → JWT required
/other-protected-api   → JWT required
```

---

# 30. `permitAll()` Does Not Bypass Filters

An important concept discovered during implementation:

```java
permitAll()
```

does not mean the request bypasses the entire Spring Security filter chain.

The JWT filter can still execute.

For example:

```text
POST /register
       │
       ▼
JWT Filter
       │
       ├── No JWT → Continue
       │
       ▼
Authorization
       │
       ▼
permitAll()
       │
       ▼
Controller
```

Therefore the JWT filter should not reject requests merely because a JWT is missing.

---

# 31. Role-Based Authorization

The application uses:

```text
ROLE_USER
```

as the user's authority.

The JWT contains:

```text
role = ROLE_USER
```

The JWT filter converts this into:

```java
new SimpleGrantedAuthority("ROLE_USER")
```

Spring Security then stores it in the `Authentication`.

---

# 32. `@PreAuthorize`

Method-level authorization was enabled using:

```java
@EnableMethodSecurity
```

A protected method can use:

```java
@PreAuthorize("hasRole('USER')")
```

The flow is:

```text
JWT
 │
 ▼
JwtAuthenticationFilter
 │
 ▼
ROLE_USER
 │
 ▼
SecurityContextHolder
 │
 ▼
@PreAuthorize("hasRole('USER')")
 │
 ▼
Authorization Decision
```

### Why `hasRole("USER")` instead of `hasRole("ROLE_USER")`?

Spring Security automatically adds the `ROLE_` prefix for `hasRole()`.

Therefore:

```java
hasRole("USER")
```

checks:

```text
ROLE_USER
```

Using:

```java
hasRole("ROLE_USER")
```

would result in an incorrect role lookup.

---

# 33. Final Security Configuration

The final configuration connects all of these components:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DaoAuthenticationProvider authenticationProvider)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authenticationProvider(authenticationProvider)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/secure-gate/register",
                    "/api/secure-gate/login",
                    "/api/secure-gate/verification-token"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }
}
```

---

# 34. Complete Registration Flow

```text
                    REGISTRATION

Client
  │
  │ email + password
  ▼
Register Controller
  │
  ▼
SecureGateService
  │
  ├── Check duplicate email
  │
  ├── BCrypt password
  │
  ├── Save User
  │
  ├── Generate VerificationToken
  │
  └── Save VerificationToken
  │
  ▼
Registration Response
```

---

# 35. Complete Email Verification Flow

```text
                 EMAIL VERIFICATION

User clicks verification link
              │
              ▼
Verification Controller
              │
              ▼
      SecureGateService
              │
              ▼
 Find token in database
              │
       ┌──────┴──────┐
       │             │
    Invalid        Valid
       │             │
    Reject           ▼
              Check used
                    │
              Check expiry
                    │
                    ▼
             Get User
                    │
                    ▼
          user.enabled = true
                    │
                    ▼
          token.used = true
                    │
                    ▼
              Save changes
```

---

# 36. Complete Login Flow

```text
                       LOGIN

Client
  │
  │ email + password
  ▼
Login Controller
  │
  ▼
SecureGateService
  │
  ├── Find user
  │
  ├── Check email verification
  │
  ▼
AuthenticationManager
  │
  ▼
DaoAuthenticationProvider
  │
  ├── CustomUserDetailsService
  │        │
  │        ▼
  │     Database
  │
  └── PasswordEncoder
           │
           ▼
      Verify Password
           │
           ▼
    Authentication Success
           │
           ▼
       JwtService
           │
           ▼
          JWT
           │
           ▼
         Client
```

---

# 37. Complete Protected Request Flow

After login, the client uses the JWT:

```text
Authorization: Bearer <JWT>
```

The request flow becomes:

```text
Client
  │
  │ Authorization: Bearer JWT
  ▼
Tomcat
  │
  ▼
Spring Security Filter Chain
  │
  ▼
JwtAuthenticationFilter
  │
  ├── Extract JWT
  │
  ├── Validate signature
  │
  ├── Validate expiration
  │
  ├── Extract email
  │
  ├── Extract role
  │
  └── Create Authentication
          │
          ▼
   SecurityContextHolder
          │
          ▼
    DispatcherServlet
          │
          ▼
      Controller
          │
          ▼
   @PreAuthorize
          │
          ▼
      Service
          │
          ▼
    Repository
          │
          ▼
      Database
```

---

# 38. Authentication vs Authorization

One of the major concepts implemented in SecureGate is the distinction between authentication and authorization.

### Authentication

Authentication answers:

> **Who are you?**

Example:

```text
Email + Password
        │
        ▼
AuthenticationManager
        │
        ▼
Authenticated User
```

### Authorization

Authorization answers:

> **Are you allowed to perform this operation?**

Example:

```text
Authenticated User
        │
        ▼
ROLE_USER
        │
        ▼
@PreAuthorize("hasRole('USER')")
        │
        ▼
Access Granted / Denied
```

---

# 39. Responsibility of Each Component

| Component                     | Responsibility                                      |
| ----------------------------- | --------------------------------------------------- |
| `User`                        | Represents application user                         |
| `VerificationToken`           | Represents email verification token                 |
| `UserRepository`              | Database access for users                           |
| `VerificationTokenRepository` | Database access for verification tokens             |
| `SecureGateService`           | Registration, verification and login orchestration  |
| `PasswordEncoder`             | Password hashing and verification                   |
| `CustomUserDetailsService`    | Converts DB user into Spring Security `UserDetails` |
| `DaoAuthenticationProvider`   | Performs username/password authentication           |
| `AuthenticationManager`       | Entry point for authentication                      |
| `JwtService`                  | Generates and validates JWTs                        |
| `JwtAuthenticationFilter`     | Authenticates requests containing JWTs              |
| `SecurityContextHolder`       | Stores authentication for current request           |
| `SecurityFilterChain`         | Defines security behavior                           |
| `@PreAuthorize`               | Performs method-level authorization                 |

---

# 40. Why Two Authentication Mechanisms Exist

It may initially look like `AuthenticationManager` and `JwtAuthenticationFilter` are doing the same thing, but they have different responsibilities.

### Login

Credentials are supplied:

```text
Email + Password
       │
       ▼
AuthenticationManager
       │
       ▼
JWT generated
```

### Subsequent requests

Credentials are no longer supplied.

The client sends:

```text
JWT
 │
 ▼
JwtAuthenticationFilter
 │
 ▼
Authentication
```

Therefore:

```text
AuthenticationManager
        ↓
Authenticates credentials during LOGIN

JwtAuthenticationFilter
        ↓
Authenticates JWT during SUBSEQUENT REQUESTS
```

---

# 41. Final Architecture

```text
                         ┌──────────────┐
                         │    Client    │
                         └──────┬───────┘
                                │
                ┌───────────────┼────────────────┐
                │               │                │
                ▼               ▼                ▼
           REGISTER           LOGIN          PROTECTED API
                │               │                │
                ▼               ▼                ▼
           Controller       Controller       JWT Filter
                │               │                │
                ▼               ▼                ▼
        SecureGateService   Authentication   Validate JWT
                │            Manager              │
                │               │                 ▼
                │               ▼          SecurityContext
                │       DaoAuthentication        │
                │           Provider              │
                │               │                 ▼
                │               ▼            Authorization
                │       UserDetailsService        │
                │               │                 ▼
                │               ▼             Controller
                │           Database               │
                │                                 ▼
                │                              Service
                │                                 │
                ▼                                 ▼
          Verification                       Repository
             Token                               │
                │                                 ▼
                └───────────────► Database ◄──────┘
```

---

# 42. Security Decisions Made

The implementation intentionally makes the following decisions:

### Passwords

```text
Never store plain-text passwords
            ↓
Use BCrypt
```

### Authentication

```text
Do not manually authenticate credentials
            ↓
Use AuthenticationManager
            ↓
DaoAuthenticationProvider
```

### JWT

```text
Use JWT for stateless API authentication
```

### Session

```text
STATELESS
```

### Default Login

```text
formLogin disabled
```

### Basic Authentication

```text
httpBasic disabled
```

### CSRF

```text
Disabled for this stateless JWT API architecture
```

### Authorization

```text
Role-based
+
@PreAuthorize
```

### Email Verification

```text
Separate VerificationToken entity
+
One-time token usage
+
Expiration validation
```

---

# 43. What Was Improved During Implementation

The project evolved through several corrections.

### 1. Database User Table

Changed from:

```text
user
```

to:

```text
users
```

to avoid reserved-word conflicts.

### 2. Password Handling

Changed from direct password comparison to:

```text
PasswordEncoder + BCrypt
```

### 3. Authentication

Changed from manually performing password verification to:

```text
AuthenticationManager
        ↓
DaoAuthenticationProvider
        ↓
UserDetailsService
        ↓
PasswordEncoder
```

### 4. JWT Secret

Changed from generating a random key at application startup to loading a configured secret.

### 5. JWT Secret Injection

Changed from field injection with immediate field initialization to constructor injection to avoid initialization-order problems.

### 6. JWT Request Authentication

Introduced:

```text
JwtAuthenticationFilter
```

using:

```text
OncePerRequestFilter
```

### 7. Stateless Security

Configured:

```text
SessionCreationPolicy.STATELESS
```

### 8. Default Security Mechanisms

Disabled:

```text
Form Login
HTTP Basic
```

because the application uses JWT authentication.

### 9. CSRF

Disabled because the API is designed around stateless JWT authentication.

### 10. Method-Level Authorization

Added:

```text
@EnableMethodSecurity
```

and:

```text
@PreAuthorize
```

for role-based access control.

---

# 44. Key Concepts Learned

Through this implementation, the following Spring Security concepts were covered:

```text
PasswordEncoder
       ↓
BCrypt
       ↓
UserDetailsService
       ↓
UserDetails
       ↓
DaoAuthenticationProvider
       ↓
AuthenticationManager
       ↓
Authentication
       ↓
SecurityContextHolder
       ↓
JWT
       ↓
OncePerRequestFilter
       ↓
SecurityFilterChain
       ↓
Authorization
       ↓
@PreAuthorize
```

The most important architectural distinction is:

```text
LOGIN
  └── AuthenticationManager

AFTER LOGIN
  └── JwtAuthenticationFilter

AUTHORIZATION
  └── SecurityContext + @PreAuthorize
```

---

# 45. End-to-End System Flow

The entire SecureGate authentication lifecycle can be summarized as:

```text
                         USER
                           │
                           ▼
                    ┌─────────────┐
                    │  Register   │
                    └──────┬──────┘
                           │
                           ▼
                    Hash Password
                           │
                           ▼
                    Save User
                           │
                           ▼
              Generate Verification Token
                           │
                           ▼
                    Verify Email
                           │
                           ▼
                  Enable User Account
                           │
                           ▼
                         LOGIN
                           │
                           ▼
                 AuthenticationManager
                           │
                           ▼
                DaoAuthenticationProvider
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
     UserDetailsService          PasswordEncoder
              │                         │
              ▼                         │
          Database ◄────────────────────┘
              │
              ▼
       Authentication Success
              │
              ▼
          Generate JWT
              │
              ▼
             CLIENT
              │
              │ Authorization: Bearer JWT
              ▼
      JwtAuthenticationFilter
              │
              ▼
        Validate JWT
              │
              ▼
      Create Authentication
              │
              ▼
      SecurityContextHolder
              │
              ▼
         @PreAuthorize
              │
        ┌─────┴─────┐
        ▼           ▼
     Allowed      Denied
        │
        ▼
    Controller
        │
        ▼
     Service
        │
        ▼
    Repository
        │
        ▼
    Database
```

---

# 46. Final Result

SecureGate now provides a complete authentication and authorization pipeline:

```text
Registration
     ↓
Password Hashing
     ↓
Email Verification
     ↓
Spring Security Authentication
     ↓
JWT Generation
     ↓
JWT Request Authentication
     ↓
SecurityContext
     ↓
Role-Based Authorization
     ↓
Protected API
```

The implementation separates the responsibilities between **application business logic** and **Spring Security infrastructure**:

```text
SecureGateService
    └── Registration
    └── Email Verification
    └── Login orchestration

Spring Security
    └── Credential Authentication
    └── Password Verification
    └── Security Context
    └── Request Authentication
    └── Authorization

JWT
    └── Stateless authentication between requests
```

This results in a stateless, database-backed authentication architecture using Spring Security and JWT.
