package com.fairshare;

import com.fairshare.controller.GroupController;
import com.fairshare.model.Group;
import com.fairshare.model.GroupMember;
import com.fairshare.model.User;
import com.fairshare.repository.UserRepository;
import com.fairshare.service.GroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupControllerTests {

    @Mock
    private GroupService groupService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupController groupController;

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

    private Group makeGroup(Long id, String name, String type, String inviteCode) {
        Group g = new Group();
        g.setId(id);
        g.setName(name);
        g.setType(type);
        g.setInviteCode(inviteCode);
        return g;
    }

    private GroupMember makeGroupMember(Group group, User user, String role) {
        GroupMember gm = new GroupMember();
        gm.setGroup(group);
        gm.setUser(user);
        gm.setRole(role);
        return gm;
    }

    // =========================================================
    // getGroupMembers
    // =========================================================

    @Test
    void testGetGroupMembersSuccess() {
        Group group = makeGroup(10L, "Trip Group", "Travel", "ABC12345");
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        GroupMember member = makeGroupMember(group, user, "ADMIN");

        when(groupService.getGroupMembers(10L)).thenReturn(List.of(member));

        ResponseEntity<?> response = groupController.getGroupMembers(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<GroupMember> body = (List<GroupMember>) response.getBody();

        assertEquals(1, body.size());
        assertEquals("ADMIN", body.get(0).getRole());
    }

    @Test
    void testGetGroupMembersFailure() {
        when(groupService.getGroupMembers(10L)).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<?> response = groupController.getGroupMembers(10L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("Error fetching group members"));
    }

    // =========================================================
    // getMyGroups
    // =========================================================

    @Test
    void testGetMyGroupsSuccess() {
        Group group = makeGroup(10L, "Roommates", "Home", "ROOM1234");
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        GroupMember membership = makeGroupMember(group, user, "ADMIN");

        when(groupService.getUserGroups(1L)).thenReturn(List.of(membership));

        ResponseEntity<?> response = groupController.getMyGroups(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<GroupMember> body = (List<GroupMember>) response.getBody();

        assertEquals(1, body.size());
    }

    @Test
    void testGetMyGroupsReturnsEmptyListWhenNull() {
        when(groupService.getUserGroups(1L)).thenReturn(null);

        ResponseEntity<?> response = groupController.getMyGroups(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<GroupMember> body = (List<GroupMember>) response.getBody();

        assertTrue(body.isEmpty());
    }

    // =========================================================
    // createGroup
    // =========================================================

    @Test
    void testCreateGroupSuccess() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        Group group = makeGroup(10L, "Trip Group", "Travel", "ABC12345");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(groupService.createGroup("Trip Group", "Travel", user)).thenReturn(group);

        ResponseEntity<?> response = groupController.createGroup("Trip Group", "Travel", 1L);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(group, response.getBody());
    }

    @Test
    void testCreateGroupFailsWhenNameEmpty() {
        ResponseEntity<?> response = groupController.createGroup("   ", "Travel", 1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Group name cannot be empty.", response.getBody());
    }

    @Test
    void testCreateGroupFailsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = groupController.createGroup("Trip Group", "Travel", 1L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("User not found."));
    }

    // =========================================================
    // joinGroup
    // =========================================================

    @Test
    void testJoinGroupSuccess() {
        User user = makeUser(2L, "Jay", "jay@example.com");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(groupService.joinGroup("ABC12345", user)).thenReturn("Joined group successfully.");

        ResponseEntity<String> response = groupController.joinGroup(" abc12345 ", 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Joined group successfully.", response.getBody());

        verify(groupService).joinGroup("ABC12345", user);
    }

    @Test
    void testJoinGroupFailsWhenUserNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = groupController.joinGroup("ABC12345", 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User not found.", response.getBody());
    }

    @Test
    void testJoinGroupBadRequestFromService() {
        User user = makeUser(2L, "Jay", "jay@example.com");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(groupService.joinGroup("ABC12345", user)).thenThrow(new RuntimeException("Invalid group code."));

        ResponseEntity<String> response = groupController.joinGroup("ABC12345", 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid group code.", response.getBody());
    }

    // =========================================================
    // updateGroupName
    // =========================================================

    @Test
    void testUpdateGroupNameSuccess() {
        Group updated = makeGroup(10L, "New Name", "Travel", "CODE1234");

        when(groupService.updateGroupName(10L, "New Name", 1L)).thenReturn(updated);

        ResponseEntity<?> response = groupController.updateGroupName(10L, "New Name", 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updated, response.getBody());
    }

    @Test
    void testUpdateGroupNameFailsWhenEmpty() {
        ResponseEntity<?> response = groupController.updateGroupName(10L, "   ", 1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("New group name cannot be empty.", response.getBody());
    }

    @Test
    void testUpdateGroupNameForbidden() {
        when(groupService.updateGroupName(10L, "New Name", 1L))
                .thenThrow(new RuntimeException("Only ADMIN can rename the group."));

        ResponseEntity<?> response = groupController.updateGroupName(10L, "New Name", 1L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Only ADMIN can rename the group.", response.getBody());
    }

    // =========================================================
    // leaveGroup
    // =========================================================

    @Test
    void testLeaveGroupSuccess() {
        ResponseEntity<?> response = groupController.leaveGroup(10L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Successfully left the group.", response.getBody());

        verify(groupService).leaveGroup(10L, 2L);
    }

    @Test
    void testLeaveGroupBadRequest() {
        doThrow(new RuntimeException("You are not a member of this group."))
                .when(groupService).leaveGroup(10L, 2L);

        ResponseEntity<?> response = groupController.leaveGroup(10L, 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("You are not a member of this group.", response.getBody());
    }

    // =========================================================
    // deleteGroup
    // =========================================================

    @Test
    void testDeleteGroupSuccess() {
        ResponseEntity<?> response = groupController.deleteGroup(10L, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Group deleted successfully.", response.getBody());

        verify(groupService).deleteGroup(10L, 1L);
    }

    @Test
    void testDeleteGroupForbidden() {
        doThrow(new RuntimeException("Only ADMIN can delete this group."))
                .when(groupService).deleteGroup(10L, 1L);

        ResponseEntity<?> response = groupController.deleteGroup(10L, 1L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Only ADMIN can delete this group.", response.getBody());
    }
}