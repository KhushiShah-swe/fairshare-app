package com.fairshare.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "group_members")
public class GroupMember {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    // This stops the user from trying to load their memberships again
    @JsonIgnoreProperties({"memberships", "password", "email"}) 
    private User user;

   @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    // This allows group info to be sent, but stops the group from loading its members again
    @JsonIgnoreProperties("members") 
    private Group group;

    private String role; // "ADMIN" or "MEMBER"

    public GroupMember() {}

    public GroupMember(User user, Group group, String role) {
        this.user = user;
        this.group = group;
        this.role = role;
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}