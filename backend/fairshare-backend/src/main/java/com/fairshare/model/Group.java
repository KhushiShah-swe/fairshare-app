package com.fairshare.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Entity
@Table(name = "app_groups") 
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // New field for group classification (e.g., Household, Trip, Team)
    private String type;

    // Updated: length = 6 ensures the DB column is optimized for 6 characters
    @Column(name = "invite_code", unique = true, length = 6)
    private String inviteCode;

    // Relationship to GroupMember
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("group") 
    private List<GroupMember> members;

    public Group() {}

    public Group(String name, String inviteCode, String type) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.type = type;
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public List<GroupMember> getMembers() { return members; }
    public void setMembers(List<GroupMember> members) { this.members = members; }
}