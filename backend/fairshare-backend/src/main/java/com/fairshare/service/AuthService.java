package com.fairshare.service;

import com.fairshare.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fairshare.repository.ExpenseRepository;
import com.fairshare.repository.ExpenseSplitRepository;
import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
import com.fairshare.payload.ExpenseRequest;

import com.fairshare.model.User;
import com.fairshare.repository.UserRepository;
import com.fairshare.payload.SignupRequest;
import com.fairshare.payload.LoginRequest;


import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class AuthService {
    @Autowired private UserRepository userRepo;

    public User signup(SignupRequest req) {
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(req.getPassword()); // plain text for now
        return userRepo.save(u);
    }

    public User login(LoginRequest req) {
        return userRepo.findByEmailAndPassword(req.getEmail(), req.getPassword())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
    }
}
