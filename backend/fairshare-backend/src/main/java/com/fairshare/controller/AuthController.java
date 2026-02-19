package com.fairshare.controller;

import com.fairshare.service.AuthService;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpSession;

import com.fairshare.model.User;
import com.fairshare.model.Group;
import com.fairshare.payload.SignupRequest;
import com.fairshare.payload.LoginRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired private AuthService authService;

    @PostMapping("/signup")
    public User signup(@RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public User login(@RequestBody LoginRequest req, HttpSession session) {
        User user = authService.login(req);
        session.setAttribute("user", user);
        return user;
    }
}
