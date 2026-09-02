package com.project.securegate.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long  id;
    private String username;
    private String email;
    private String hashPassword;

    public User() {
    }

    public User(Long id, String username, String email, String hashPassword) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.hashPassword = hashPassword;
    }

    public User(String username, String email, String hashPassword) {
        this.username = username;
        this.email = email;
        this.hashPassword = hashPassword;
    }


    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setHashPassword(String hashPassword) {
        this.hashPassword = hashPassword;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getHashPassword() {
        return hashPassword;
    }


}
