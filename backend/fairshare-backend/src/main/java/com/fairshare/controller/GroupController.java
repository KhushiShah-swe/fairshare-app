package com.fairshare.controller;

import com.fairshare.model.Group;
import com.fairshare.model.GroupMember;
import com.fairshare.model.User;
import com.fairshare.service.GroupService;
import com.fairshare.repository.UserRepository;
import com.fairshare.dto.ExpenseDTO;
import com.fairshare.dto.BalanceDTO;
import com.fairshare.dto.GroupDetailsDTO;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class GroupController {

    @Autowired 
    private GroupService groupService;

    @Autowired
    private UserRepository userRepository;

    // ---------------- Existing Endpoints ----------------

    @GetMapping("/group-members/{groupId}")
    public ResponseEntity<?> getGroupMembers(@PathVariable Long groupId) {
        try {
            List<GroupMember> members = groupService.getGroupMembers(groupId);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching group members: " + e.getMessage());
        }
    }

    @GetMapping("/user-groups/{userId}")
    public ResponseEntity<?> getMyGroups(@PathVariable Long userId) {
        try {
            List<GroupMember> memberships = groupService.getUserGroups(userId);
            return ResponseEntity.ok(memberships != null ? memberships : List.of());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching groups: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createGroup(
            @RequestParam String name, 
            @RequestParam String type, 
            @RequestParam Long userId) {
        try {
            if (name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Group name cannot be empty.");
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found."));
            Group createdGroup = groupService.createGroup(name.trim(), type.trim(), user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResponseEntity<String> joinGroup(
            @RequestParam String code, 
            @RequestParam Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found."));
            String result = groupService.joinGroup(code.trim().toUpperCase(), user);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @PutMapping("/{groupId}/update-name")
    public ResponseEntity<?> updateGroupName(
            @PathVariable Long groupId,
            @RequestParam String newName,
            @RequestParam Long userId) {
        try {
            if (newName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("New group name cannot be empty.");
            }
            Group updatedGroup = groupService.updateGroupName(groupId, newName.trim(), userId);
            return ResponseEntity.ok(updatedGroup);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating group name.");
        }
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<?> leaveGroup(@PathVariable Long groupId, @RequestParam Long userId) {
        try {
            groupService.leaveGroup(groupId, userId);
            return ResponseEntity.ok("Successfully left the group.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error leaving group.");
        }
    }

    @DeleteMapping("/{groupId}/delete")
    public ResponseEntity<?> deleteGroup(@PathVariable Long groupId, @RequestParam Long userId) {
        try {
            groupService.deleteGroup(groupId, userId);
            return ResponseEntity.ok("Group deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting group.");
        }
    }

    // ---------------- NEW: Group Details with Expenses & Balances ----------------

    @GetMapping("/{groupId}/details")
    public ResponseEntity<?> getGroupDetails(@PathVariable Long groupId) {
        try {
            GroupDetailsDTO groupDetails = groupService.getGroupDetails(groupId);
            return ResponseEntity.ok(groupDetails);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching group details: " + e.getMessage());
        }
    }
}
