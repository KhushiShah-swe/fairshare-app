package com.fairshare;

import com.fairshare.controller.AuthController;
import com.fairshare.model.User;
import com.fairshare.payload.LoginRequest;
import com.fairshare.payload.SignupRequest;
import com.fairshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTests {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    // ----------------------------
    // Helpers
    // ----------------------------

    private User makeUser(Long id, String name, String email) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setEmail(email);
        return u;
    }

    // =========================================================
    // signup
    // =========================================================

    @Test
    void testSignupSuccess() {
        SignupRequest req = new SignupRequest();
        req.setName("Khushi");
        req.setEmail("khushi@example.com");
        req.setPassword("123456");

        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setPassword("123456");

        when(authService.signup(req)).thenReturn(user);

        User response = authController.signup(req);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Khushi", response.getName());
        assertEquals("khushi@example.com", response.getEmail());
    }

    // =========================================================
    // login
    // =========================================================

    @Test
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setEmail("khushi@example.com");
        req.setPassword("123456");

        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setPassword("123456");

        HttpSession session = mock(HttpSession.class);

        when(authService.login(req)).thenReturn(user);

        User response = authController.login(req, session);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        verify(session).setAttribute("user", user);
    }

    // =========================================================
    // update Zelle info
    // =========================================================

    @Test
    void testUpdateZelleInfoSuccess() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setZelleEmail("zelle@example.com");
        user.setZellePhone("1234567890");
        user.setHasZelleQr(true);

        when(authService.updateZelleInfo(1L, "zelle@example.com", "1234567890")).thenReturn(user);

        ResponseEntity<?> response = authController.updateZelleInfo(1L, "zelle@example.com", "1234567890");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Zelle info updated successfully", body.get("message"));
        assertEquals(1L, body.get("userId"));
        assertEquals("zelle@example.com", body.get("zelleEmail"));
        assertEquals("1234567890", body.get("zellePhone"));
        assertEquals(true, body.get("hasZelleQr"));
    }

    @Test
    void testUpdateZelleInfoNotFound() {
        when(authService.updateZelleInfo(1L, "zelle@example.com", "1234567890"))
                .thenThrow(new RuntimeException("User not found with id: 1"));

        ResponseEntity<?> response = authController.updateZelleInfo(1L, "zelle@example.com", "1234567890");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("User not found with id: 1", body.get("error"));
    }

   @Test
void testUpdateZelleInfoRuntimeExceptionHandledAsNotFound() {
    when(authService.updateZelleInfo(1L, "zelle@example.com", "1234567890"))
            .thenThrow(new IllegalStateException("Unexpected failure"));

    ResponseEntity<?> response = authController.updateZelleInfo(1L, "zelle@example.com", "1234567890");

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();

    assertEquals("Unexpected failure", body.get("error"));
}

    // =========================================================
    // upload Zelle QR
    // =========================================================

    @Test
    void testUploadZelleQrSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "zelle.png",
                "image/png",
                "qrdata".getBytes()
        );

        ResponseEntity<?> response = authController.uploadZelleQr(1L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Zelle QR uploaded successfully", body.get("message"));
        assertEquals(1L, body.get("userId"));
        assertEquals("zelle.png", body.get("fileName"));
        assertEquals("image/png", body.get("contentType"));
        assertEquals(true, body.get("hasZelleQr"));

        verify(authService).uploadZelleQr(1L, file.getBytes(), "zelle.png", "image/png");
    }

    @Test
    void testUploadZelleQrFailsWhenFileEmpty() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "image/png",
                new byte[0]
        );

        ResponseEntity<?> response = authController.uploadZelleQr(1L, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Zelle QR file is required.", body.get("error"));
    }

    @Test
    void testUploadZelleQrBadRequestFromService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.txt",
                "text/plain",
                "bad".getBytes()
        );

        doThrow(new RuntimeException("Only image files are allowed for Zelle QR."))
                .when(authService)
                .uploadZelleQr(1L, file.getBytes(), "bad.txt", "text/plain");

        ResponseEntity<?> response = authController.uploadZelleQr(1L, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Only image files are allowed for Zelle QR.", body.get("error"));
    }

    @Test
void testUploadZelleQrRuntimeExceptionHandledAsBadRequest() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "zelle.png",
            "image/png",
            "qrdata".getBytes()
    );

    doThrow(new IllegalStateException("Unexpected failure"))
            .when(authService)
            .uploadZelleQr(1L, file.getBytes(), "zelle.png", "image/png");

    ResponseEntity<?> response = authController.uploadZelleQr(1L, file);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();

    assertEquals("Unexpected failure", body.get("error"));
}

    // =========================================================
    // get Zelle info
    // =========================================================

    @Test
    void testGetZelleInfoSuccess() {
        Map<String, Object> info = Map.of(
                "userId", 1L,
                "name", "Khushi",
                "email", "khushi@example.com",
                "zelleEmail", "zelle@example.com",
                "zellePhone", "1234567890",
                "hasZelleQr", true
        );

        when(authService.getZelleInfo(1L)).thenReturn(info);

        ResponseEntity<?> response = authController.getZelleInfo(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(info, response.getBody());
    }

    @Test
    void testGetZelleInfoNotFound() {
        when(authService.getZelleInfo(1L))
                .thenThrow(new RuntimeException("User not found with id: 1"));

        ResponseEntity<?> response = authController.getZelleInfo(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("User not found with id: 1", body.get("error"));
    }

    // =========================================================
    // get Zelle QR
    // =========================================================

    @Test
    void testGetZelleQrSuccess() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setZelleQrData("qrdata".getBytes());
        user.setZelleQrFileName("zelle.png");
        user.setZelleQrContentType("image/png");

        when(authService.getUserWithZelleQr(1L)).thenReturn(user);

        ResponseEntity<?> response = authController.getZelleQr(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ByteArrayResource);
        assertEquals("inline; filename=\"zelle.png\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void testGetZelleQrNotFoundWhenNoData() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setZelleQrData(null);

        when(authService.getUserWithZelleQr(1L)).thenReturn(user);

        ResponseEntity<?> response = authController.getZelleQr(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("No Zelle QR found for this user.", body.get("error"));
    }

    @Test
    void testGetZelleQrUserNotFound() {
        when(authService.getUserWithZelleQr(1L))
                .thenThrow(new RuntimeException("User not found with id: 1"));

        ResponseEntity<?> response = authController.getZelleQr(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("User not found with id: 1", body.get("error"));
    }

    // =========================================================
    // delete Zelle QR
    // =========================================================

    @Test
    void testDeleteZelleQrSuccess() {
        ResponseEntity<?> response = authController.deleteZelleQr(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Zelle QR deleted successfully", body.get("message"));
        verify(authService).deleteZelleQr(1L);
    }

    @Test
    void testDeleteZelleQrNotFound() {
        doThrow(new RuntimeException("User not found with id: 1"))
                .when(authService).deleteZelleQr(1L);

        ResponseEntity<?> response = authController.deleteZelleQr(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("User not found with id: 1", body.get("error"));
    }
}