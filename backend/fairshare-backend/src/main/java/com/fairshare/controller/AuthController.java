package com.fairshare.controller;

import com.fairshare.service.AuthService;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

import com.fairshare.model.User;
import com.fairshare.payload.SignupRequest;
import com.fairshare.payload.LoginRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

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

    // =========================
    // Sprint 3: Zelle Info Endpoints
    // =========================

    /**
     * PUT: Update Zelle email and/or phone for a user.
     */
    @PutMapping("/{userId}/zelle")
    public ResponseEntity<?> updateZelleInfo(
            @PathVariable Long userId,
            @RequestParam(required = false) String zelleEmail,
            @RequestParam(required = false) String zellePhone
    ) {
        try {
            User updatedUser = authService.updateZelleInfo(userId, zelleEmail, zellePhone);
            return ResponseEntity.ok(Map.of(
                    "message", "Zelle info updated successfully",
                    "userId", updatedUser.getId(),
                    "zelleEmail", updatedUser.getZelleEmail() != null ? updatedUser.getZelleEmail() : "",
                    "zellePhone", updatedUser.getZellePhone() != null ? updatedUser.getZellePhone() : "",
                    "hasZelleQr", Boolean.TRUE.equals(updatedUser.getHasZelleQr())
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating Zelle info: " + e.getMessage()));
        }
    }

    /**
     * POST: Upload or replace Zelle QR image for a user.
     * Form-Data: key=file
     */
    @PostMapping(value = "/{userId}/zelle/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadZelleQr(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Zelle QR file is required."));
            }

            String contentType = file.getContentType();
            String originalName = file.getOriginalFilename();

            authService.uploadZelleQr(userId, file.getBytes(), originalName, contentType);

            return ResponseEntity.ok(Map.of(
                    "message", "Zelle QR uploaded successfully",
                    "userId", userId,
                    "fileName", originalName != null ? originalName : "",
                    "contentType", contentType != null ? contentType : "",
                    "hasZelleQr", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error uploading Zelle QR: " + e.getMessage()));
        }
    }

    /**
     * GET: Fetch Zelle info for a user.
     */
    @GetMapping("/{userId}/zelle")
    public ResponseEntity<?> getZelleInfo(@PathVariable Long userId) {
        try {
            Map<String, Object> info = authService.getZelleInfo(userId);
            return ResponseEntity.ok(info);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching Zelle info: " + e.getMessage()));
        }
    }

    /**
     * GET: View/download Zelle QR image for a user.
     */
    @GetMapping("/{userId}/zelle/qr")
    public ResponseEntity<?> getZelleQr(@PathVariable Long userId) {
        try {
            User user = authService.getUserWithZelleQr(userId);

            if (user.getZelleQrData() == null || user.getZelleQrData().length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No Zelle QR found for this user."));
            }

            String contentType = user.getZelleQrContentType() != null
                    ? user.getZelleQrContentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            String fileName = (user.getZelleQrFileName() != null && !user.getZelleQrFileName().isBlank())
                    ? user.getZelleQrFileName()
                    : ("user_" + userId + "_zelle_qr");

            ByteArrayResource resource = new ByteArrayResource(user.getZelleQrData());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching Zelle QR: " + e.getMessage()));
        }
    }

    /**
     * DELETE: Remove Zelle QR for a user.
     */
    @DeleteMapping("/{userId}/zelle/qr")
    public ResponseEntity<?> deleteZelleQr(@PathVariable Long userId) {
        try {
            authService.deleteZelleQr(userId);
            return ResponseEntity.ok(Map.of("message", "Zelle QR deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deleting Zelle QR: " + e.getMessage()));
        }
    }
}