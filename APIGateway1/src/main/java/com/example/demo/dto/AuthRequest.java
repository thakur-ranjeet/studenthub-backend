package com.example.demo.dto;

public class AuthRequest {
    private String username;
    private String password;
    private String role; //added role
    private String email; // optional, used to send the password reset link

    public AuthRequest() {}

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
 //  Add Getter and Setter for role
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
