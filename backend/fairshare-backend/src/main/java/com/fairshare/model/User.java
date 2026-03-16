package com.fairshare.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String password;

    // =========================
    // Sprint 3: Zelle Payment Info
    // =========================
    private String zelleEmail;
    private String zellePhone;

    @Lob
    @Column(name = "zelle_qr_data", columnDefinition = "LONGBLOB")
    private byte[] zelleQrData;

    private String zelleQrFileName;
    private String zelleQrContentType;
    private Boolean hasZelleQr = false;

    // =========================
    // Existing getters and setters
    // =========================
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // =========================
    // Zelle getters and setters
    // =========================
    public String getZelleEmail() {
        return zelleEmail;
    }

    public void setZelleEmail(String zelleEmail) {
        this.zelleEmail = zelleEmail;
    }

    public String getZellePhone() {
        return zellePhone;
    }

    public void setZellePhone(String zellePhone) {
        this.zellePhone = zellePhone;
    }

    public byte[] getZelleQrData() {
        return zelleQrData;
    }

    public void setZelleQrData(byte[] zelleQrData) {
        this.zelleQrData = zelleQrData;
    }

    public String getZelleQrFileName() {
        return zelleQrFileName;
    }

    public void setZelleQrFileName(String zelleQrFileName) {
        this.zelleQrFileName = zelleQrFileName;
    }

    public String getZelleQrContentType() {
        return zelleQrContentType;
    }

    public void setZelleQrContentType(String zelleQrContentType) {
        this.zelleQrContentType = zelleQrContentType;
    }

    public Boolean getHasZelleQr() {
        return hasZelleQr;
    }

    public void setHasZelleQr(Boolean hasZelleQr) {
        this.hasZelleQr = hasZelleQr;
    }
}