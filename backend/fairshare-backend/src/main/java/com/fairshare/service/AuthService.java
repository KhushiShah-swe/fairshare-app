package com.fairshare.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.fairshare.model.User;
import com.fairshare.repository.UserRepository;
import com.fairshare.payload.SignupRequest;
import com.fairshare.payload.LoginRequest;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepo;

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

    // =========================
    // Sprint 3: Zelle Info
    // =========================

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

    @Transactional
    public User updateZelleInfo(Long userId, String zelleEmail, String zellePhone) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setZelleEmail(zelleEmail != null && !zelleEmail.isBlank() ? zelleEmail.trim() : null);
        user.setZellePhone(zellePhone != null && !zellePhone.isBlank() ? zellePhone.trim() : null);

        return userRepo.save(user);
    }

    @Transactional
    public void uploadZelleQr(Long userId, byte[] fileBytes, String originalFileName, String contentType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("Zelle QR file is empty.");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new RuntimeException("Zelle QR contentType is required.");
        }

        boolean isImage = contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        if (!isImage) {
            throw new RuntimeException("Only image files are allowed for Zelle QR.");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setZelleQrData(fileBytes);
        user.setZelleQrFileName(originalFileName);
        user.setZelleQrContentType(contentType);
        user.setHasZelleQr(true);

        userRepo.save(user);
    }

    @Transactional
    public void deleteZelleQr(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setZelleQrData(null);
        user.setZelleQrFileName(null);
        user.setZelleQrContentType(null);
        user.setHasZelleQr(false);

        userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getZelleInfo(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getId());
        info.put("name", user.getName());
        info.put("email", user.getEmail());
        info.put("zelleEmail", user.getZelleEmail());
        info.put("zellePhone", user.getZellePhone());
        info.put("hasZelleQr", Boolean.TRUE.equals(user.getHasZelleQr()));
        info.put("zelleQrFileName", user.getZelleQrFileName());
        info.put("zelleQrContentType", user.getZelleQrContentType());

        return info;
    }

    @Transactional(readOnly = true)
    public User getUserWithZelleQr(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }
}