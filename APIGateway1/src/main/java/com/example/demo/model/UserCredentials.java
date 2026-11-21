package com.example.demo.model;


import jakarta.persistence.*;

@Entity
@Table(name = "user_credentials")
public class UserCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String role; // We will use this for Phase 2 (e.g., "ROLE_ADMIN", "ROLE_STUDENT")

    // Where the password reset link is sent. Optional: older accounts do not have one.
    @Column(unique = true)
    private String email;

    // Constructors
    public UserCredentials() {}

    public UserCredentials(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
