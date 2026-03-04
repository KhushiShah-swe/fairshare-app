package com.fairshare.service;

import com.fairshare.model.Group;
import com.fairshare.model.GroupMember;
import com.fairshare.model.User;
import com.fairshare.repository.GroupRepository;
import com.fairshare.repository.GroupMemberRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepo;

    @Autowired
    private GroupMemberRepository memberRepo;

    // ---------------- READ ----------------

    @Transactional(readOnly = true)
    public List<GroupMember> getGroupMembers(Long groupId) {
        return memberRepo.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupMember> getUserGroups(Long userId) {
        return memberRepo.findByUserId(userId);
    }

    // ---------------- CREATE / JOIN ----------------

    @Transactional
    public Group createGroup(String name, String type, User creator) {
        Group g = new Group();
        g.setName(name);
        g.setType(type);
        g.setInviteCode(generateInviteCode());

        Group saved = groupRepo.save(g);

        GroupMember gm = new GroupMember();
        gm.setGroup(saved);
        gm.setUser(creator);
        gm.setRole("ADMIN"); // ✅ IMPORTANT for settings/admin buttons
        memberRepo.save(gm);

        return saved;
    }

    @Transactional
    public String joinGroup(String code, User user) {
        Group group = groupRepo.findByInviteCode(code)
                .orElseThrow(() -> new RuntimeException("Invalid group code."));

        // Prevent duplicate join
        GroupMember existing = findMembership(group.getId(), user.getId());
        if (existing != null) {
            return "You are already a member of this group.";
        }

        GroupMember gm = new GroupMember();
        gm.setGroup(group);
        gm.setUser(user);
        gm.setRole("MEMBER"); // ✅ joiners are MEMBER
        memberRepo.save(gm);

        return "Joined group successfully.";
    }

    // ---------------- UPDATE ----------------

    @Transactional
    public Group updateGroupName(Long groupId, String newName, Long userId) {
        GroupMember membership = findMembership(groupId, userId);
        if (membership == null) {
            throw new RuntimeException("You are not a member of this group.");
        }

        // ✅ Only ADMIN can rename
        if (membership.getRole() == null || !membership.getRole().equalsIgnoreCase("ADMIN")) {
            throw new RuntimeException("Only ADMIN can rename the group.");
        }

        Group group = groupRepo.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found."));

        group.setName(newName);
        return groupRepo.save(group);
    }

    // ---------------- LEAVE / DELETE ----------------

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        GroupMember membership = findMembership(groupId, userId);
        if (membership == null) {
            throw new RuntimeException("You are not a member of this group.");
        }

        memberRepo.delete(membership);
    }

    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        GroupMember membership = findMembership(groupId, userId);
        if (membership == null) {
            throw new RuntimeException("You are not allowed to delete this group.");
        }

        // ✅ Only ADMIN can delete
        if (membership.getRole() == null || !membership.getRole().equalsIgnoreCase("ADMIN")) {
            throw new RuntimeException("Only ADMIN can delete this group.");
        }

        // Delete members first (FK constraints)
        List<GroupMember> members = memberRepo.findByGroupId(groupId);
        memberRepo.deleteAll(members);

        groupRepo.deleteById(groupId);
    }

    // ---------------- HELPERS ----------------

    private GroupMember findMembership(Long groupId, Long userId) {
        return memberRepo.findByGroupId(groupId).stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}