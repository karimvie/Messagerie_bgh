package org.example.api.controller;

import org.example.api.dto.LoginRequest;
import org.example.api.dto.RegisterRequest;
import org.example.api.service.AuthApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthApiService authService;

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        boolean success = authService.login(request.username, request.password);
        return success ? "Login successful" : "Login failed";
    }

    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request) {
        boolean created = authService.register(request.username, request.password);
        return created ? "User created" : "Error creating user";
    }

    @DeleteMapping("/delete/{username}")
    public String deleteUser(@PathVariable String username) {
        boolean deleted = authService.delete(username);
        return deleted ? "User deleted" : "User not found";
    }
}
