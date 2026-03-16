package com.fairshare;


import jakarta.servlet.http.HttpSession;
import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
import com.fairshare.model.Group;
import com.fairshare.model.GroupMember;
import com.fairshare.model.User;
import com.fairshare.payload.ExpenseRequest;
import com.fairshare.payload.LoginRequest;
import com.fairshare.payload.SignupRequest;
import com.fairshare.repository.ExpenseRepository;
import com.fairshare.repository.ExpenseSplitRepository;
import com.fairshare.repository.GroupMemberRepository;
import com.fairshare.repository.GroupRepository;
import com.fairshare.repository.UserRepository;
import com.fairshare.service.AuthService;
import com.fairshare.service.BalanceService;
import com.fairshare.service.ExpenseService;
import com.fairshare.service.GroupService;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

// import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

// @SpringBootTest

public class FairShareBackendTests {

    @Mock
    private ExpenseRepository expenseRepo;

    @Mock
    private ExpenseSplitRepository splitRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private GroupRepository groupRepo;

    @Mock
    private GroupMemberRepository memberRepo;

    @InjectMocks
    private ExpenseService expenseService;

    @InjectMocks
    private GroupService groupService;

    @InjectMocks
    private AuthService authService;

    @InjectMocks
    private BalanceService balanceService;

   
   
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

    private Expense makeExpense(Long id, Long groupId, double amount, User paidBy, String desc) {
        Expense e = new Expense();
        e.setId(id);
        e.setGroupId(groupId);
        e.setAmount(amount);
        e.setPaidBy(paidBy);
        e.setDescription(desc);
        e.setCategory("Food");
        e.setExpenseDate(LocalDate.of(2026, 3, 15));
        return e;
    }

    private ExpenseRequest makeEqualRequest() {
        ExpenseRequest req = new ExpenseRequest();
        req.setGroupId(10L);
        req.setAmount(90.0);
        req.setDescription("Pizza Dinner");
        req.setPaidBy(1L);
        req.setCategory("Food");
        req.setNotes("Shared dinner");
        req.setExpenseDate("2026-03-15");
        req.setSplitType("EQUAL");
        req.setParticipants(Arrays.asList(1L, 2L, 3L));
        return req;
    }

    private ExpenseRequest makePercentageRequest() {
        ExpenseRequest req = new ExpenseRequest();
        req.setGroupId(10L);
        req.setAmount(100.0);
        req.setDescription("Trip Hotel");
        req.setPaidBy(1L);
        req.setCategory("Travel");
        req.setNotes("Weekend stay");
        req.setExpenseDate("2026-03-15");
        req.setSplitType("PERCENTAGE");
        req.setParticipants(Arrays.asList(1L, 2L));

        Map<String, Double> percentages = new HashMap<>();
        percentages.put("1", 40.0);
        percentages.put("2", 60.0);
        req.setPercentages(percentages);

        return req;
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
    // ExpenseService Tests
    // =========================================================

    @Test
    void testGetExpenseByIdFound() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(101L, 10L, 90.0, payer, "Pizza");

        when(expenseRepo.findById(101L)).thenReturn(Optional.of(expense));

        Expense result = expenseService.getExpenseById(101L);

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("Pizza", result.getDescription());
    }

    @Test
    void testGetExpenseByIdNotFound() {
        when(expenseRepo.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> expenseService.getExpenseById(999L));

        assertTrue(ex.getMessage().contains("Expense not found"));
    }

    @Test
    void testAddExpenseEqualSplit() {
        ExpenseRequest req = makeEqualRequest();
        User payer = makeUser(1L, "Khushi", "khushi@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(payer));
        when(expenseRepo.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(500L);
            return e;
        });

        Expense saved = expenseService.addExpense(req);

        assertNotNull(saved);
        assertEquals(500L, saved.getId());
        assertEquals(10L, saved.getGroupId());
        assertEquals("Pizza Dinner", saved.getDescription());
        assertEquals(90.0, saved.getAmount());

        ArgumentCaptor<ExpenseSplit> splitCaptor = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(splitRepo, times(3)).save(splitCaptor.capture());

        List<ExpenseSplit> savedSplits = splitCaptor.getAllValues();
        assertEquals(3, savedSplits.size());

        for (ExpenseSplit split : savedSplits) {
            assertEquals(30.0, split.getAmount(), 0.001);
            assertNull(split.getPercentage());
            assertTrue(Arrays.asList(1L, 2L, 3L).contains(split.getUserId()));
        }
    }

    @Test
    void testAddExpensePercentageSplit() {
        ExpenseRequest req = makePercentageRequest();
        User payer = makeUser(1L, "Khushi", "khushi@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(payer));
        when(expenseRepo.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(501L);
            return e;
        });

        expenseService.addExpense(req);

        ArgumentCaptor<ExpenseSplit> splitCaptor = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(splitRepo, times(2)).save(splitCaptor.capture());

        List<ExpenseSplit> splits = splitCaptor.getAllValues();

        Map<Long, ExpenseSplit> splitMap = new HashMap<>();
        for (ExpenseSplit split : splits) {
            splitMap.put(split.getUserId(), split);
        }

        assertEquals(40.0, splitMap.get(1L).getAmount(), 0.001);
        assertEquals(40.0, splitMap.get(1L).getPercentage(), 0.001);

        assertEquals(60.0, splitMap.get(2L).getAmount(), 0.001);
        assertEquals(60.0, splitMap.get(2L).getPercentage(), 0.001);
    }

    @Test
    void testAddExpensePercentageSplitFailsWhenPercentagesDoNotSumTo100() {
        ExpenseRequest req = makePercentageRequest();
        User payer = makeUser(1L, "Khushi", "khushi@example.com");

        Map<String, Double> wrongPercentages = new HashMap<>();
        wrongPercentages.put("1", 20.0);
        wrongPercentages.put("2", 50.0);
        req.setPercentages(wrongPercentages);

        when(userRepo.findById(1L)).thenReturn(Optional.of(payer));
        when(expenseRepo.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(502L);
            return e;
        });

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> expenseService.addExpense(req));

        assertTrue(ex.getMessage().contains("Percentages must sum to 100"));
    }

    @Test
    void testUploadReceiptSuccess() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(200L, 10L, 45.0, payer, "Groceries");

        when(expenseRepo.findById(200L)).thenReturn(Optional.of(expense));

        byte[] data = "fake-image-data".getBytes();

        expenseService.uploadReceipt(200L, data, "receipt.jpg", "image/jpeg");

        assertArrayEquals(data, expense.getReceiptData());
        assertEquals("receipt.jpg", expense.getReceiptFileName());
        assertEquals("image/jpeg", expense.getReceiptContentType());
        assertTrue(expense.getHasReceipt());

        verify(expenseRepo).save(expense);
    }

    @Test
    void testUploadReceiptFailsForInvalidContentType() {
        byte[] data = "bad-file".getBytes();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> expenseService.uploadReceipt(200L, data, "receipt.exe", "application/octet-stream"));

        assertEquals("Only image/* or application/pdf receipts are allowed.", ex.getMessage());
    }

    @Test
    void testGetReceiptInfo() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(201L, 10L, 25.0, payer, "Taxi");
        expense.setHasReceipt(true);
        expense.setReceiptFileName("taxi.pdf");
        expense.setReceiptContentType("application/pdf");

        when(expenseRepo.findById(201L)).thenReturn(Optional.of(expense));

        Map<String, Object> info = expenseService.getReceiptInfo(201L);

        assertEquals(201L, info.get("expenseId"));
        assertEquals(true, info.get("hasReceipt"));
        assertEquals("taxi.pdf", info.get("fileName"));
        assertEquals("application/pdf", info.get("contentType"));
    }

    @Test
    void testDeleteReceiptClearsReceiptFields() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(202L, 10L, 30.0, payer, "Snacks");
        expense.setHasReceipt(true);
        expense.setReceiptFileName("snacks.jpg");
        expense.setReceiptContentType("image/jpeg");
        expense.setReceiptData("abc".getBytes());

        when(expenseRepo.findById(202L)).thenReturn(Optional.of(expense));

        expenseService.deleteReceipt(202L);

        assertNull(expense.getReceiptData());
        assertNull(expense.getReceiptFileName());
        assertNull(expense.getReceiptContentType());
        assertFalse(expense.getHasReceipt());

        verify(expenseRepo).save(expense);
    }

    @Test
    void testGetSettlementPlanByGroup() {
        User alice = makeUser(1L, "Alice", "alice@example.com");
        User bob = makeUser(2L, "Bob", "bob@example.com");

        Expense expense = makeExpense(301L, 10L, 60.0, alice, "Dinner");

        ExpenseSplit split1 = new ExpenseSplit();
        split1.setExpense(expense);
        split1.setUserId(1L);
        split1.setAmount(30.0);

        ExpenseSplit split2 = new ExpenseSplit();
        split2.setExpense(expense);
        split2.setUserId(2L);
        split2.setAmount(30.0);

        when(expenseRepo.findByGroupIdOrderByIdDesc(10L)).thenReturn(List.of(expense));
        when(splitRepo.findByExpenseId(301L)).thenReturn(Arrays.asList(split1, split2));
        when(userRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepo.findById(2L)).thenReturn(Optional.of(bob));

        List<Map<String, Object>> plan = expenseService.getSettlementPlanByGroup(10L);

        assertEquals(1, plan.size());

        Map<String, Object> instruction = plan.get(0);
        assertEquals(2L, ((Number) instruction.get("fromUserId")).longValue());
        assertEquals(1L, ((Number) instruction.get("toUserId")).longValue());
        assertEquals(30.0, ((Number) instruction.get("amount")).doubleValue(), 0.001);
        assertEquals("Bob pays Alice $30.00", instruction.get("text"));
    }

    @Test
    void testClearAllDebtsForGroup() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");

        Expense e1 = makeExpense(401L, 10L, 20.0, payer, "Coffee");
        Expense e2 = makeExpense(402L, 10L, 80.0, payer, "Lunch");

        when(expenseRepo.findByGroupIdOrderByIdDesc(10L)).thenReturn(Arrays.asList(e1, e2));

        Map<String, Object> result = expenseService.clearAllDebtsForGroup(10L);

        verify(splitRepo).deleteByExpenseId(401L);
        verify(splitRepo).deleteByExpenseId(402L);
        verify(expenseRepo).deleteById(401L);
        verify(expenseRepo).deleteById(402L);

        assertEquals("All debts cleared successfully.", result.get("message"));
        assertEquals(10L, result.get("groupId"));
        assertEquals(2, result.get("clearedExpenses"));
    }

    // =========================================================
    // GroupService Tests
    // =========================================================

    @Test
    void testCreateGroupAssignsAdminRole() {
        User creator = makeUser(1L, "Khushi", "khushi@example.com");

        when(groupRepo.save(any(Group.class))).thenAnswer(invocation -> {
            Group g = invocation.getArgument(0);
            g.setId(100L);
            return g;
        });

        Group savedGroup = groupService.createGroup("Trip Group", "Travel", creator);

        assertNotNull(savedGroup);
        assertEquals(100L, savedGroup.getId());
        assertEquals("Trip Group", savedGroup.getName());
        assertEquals("Travel", savedGroup.getType());
        assertNotNull(savedGroup.getInviteCode());
        assertEquals(8, savedGroup.getInviteCode().length());

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(memberRepo).save(memberCaptor.capture());

        GroupMember savedMembership = memberCaptor.getValue();
        assertEquals("ADMIN", savedMembership.getRole());
        assertEquals(creator, savedMembership.getUser());
        assertEquals(savedGroup, savedMembership.getGroup());
    }

    @Test
    void testJoinGroupSuccess() {
        Group group = makeGroup(10L, "Roommates", "Home", "ABC12345");
        User user = makeUser(2L, "Jay", "jay@example.com");

        when(groupRepo.findByInviteCode("ABC12345")).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupId(10L)).thenReturn(new ArrayList<>());

        String result = groupService.joinGroup("ABC12345", user);

        assertEquals("Joined group successfully.", result);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(memberRepo).save(memberCaptor.capture());

        GroupMember gm = memberCaptor.getValue();
        assertEquals("MEMBER", gm.getRole());
        assertEquals(user, gm.getUser());
        assertEquals(group, gm.getGroup());
    }

    @Test
    void testJoinGroupAlreadyMember() {
        Group group = makeGroup(10L, "Roommates", "Home", "ABC12345");
        User user = makeUser(2L, "Jay", "jay@example.com");
        GroupMember existing = makeGroupMember(group, user, "MEMBER");

        when(groupRepo.findByInviteCode("ABC12345")).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupId(10L)).thenReturn(List.of(existing));

        String result = groupService.joinGroup("ABC12345", user);

        assertEquals("You are already a member of this group.", result);
        verify(memberRepo, never()).save(any(GroupMember.class));
    }

    @Test
    void testUpdateGroupNameByAdmin() {
        Group group = makeGroup(10L, "Old Name", "Travel", "CODE1234");
        User admin = makeUser(1L, "Khushi", "khushi@example.com");
        GroupMember membership = makeGroupMember(group, admin, "ADMIN");

        when(memberRepo.findByGroupId(10L)).thenReturn(List.of(membership));
        when(groupRepo.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepo.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Group updated = groupService.updateGroupName(10L, "New Name", 1L);

        assertEquals("New Name", updated.getName());
    }

    @Test
    void testUpdateGroupNameFailsForNonAdmin() {
        Group group = makeGroup(10L, "Old Name", "Travel", "CODE1234");
        User member = makeUser(2L, "Jay", "jay@example.com");
        GroupMember membership = makeGroupMember(group, member, "MEMBER");

        when(memberRepo.findByGroupId(10L)).thenReturn(List.of(membership));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> groupService.updateGroupName(10L, "New Name", 2L));

        assertEquals("Only ADMIN can rename the group.", ex.getMessage());
    }

    @Test
    void testLeaveGroupSuccess() {
        Group group = makeGroup(10L, "Trip", "Travel", "TRIP1234");
        User member = makeUser(2L, "Jay", "jay@example.com");
        GroupMember membership = makeGroupMember(group, member, "MEMBER");

        when(memberRepo.findByGroupId(10L)).thenReturn(List.of(membership));

        groupService.leaveGroup(10L, 2L);

        verify(memberRepo).delete(membership);
    }

    @Test
    void testDeleteGroupByAdmin() {
        Group group = makeGroup(10L, "Trip", "Travel", "TRIP1234");
        User admin = makeUser(1L, "Khushi", "khushi@example.com");
        User member = makeUser(2L, "Jay", "jay@example.com");

        GroupMember adminMembership = makeGroupMember(group, admin, "ADMIN");
        GroupMember memberMembership = makeGroupMember(group, member, "MEMBER");

        when(memberRepo.findByGroupId(10L)).thenReturn(Arrays.asList(adminMembership, memberMembership));

        groupService.deleteGroup(10L, 1L);

        verify(memberRepo).deleteAll(Arrays.asList(adminMembership, memberMembership));
        verify(groupRepo).deleteById(10L);
    }

    // =========================================================
    // AuthService Tests
    // =========================================================

    @Test
    void testSignupSuccess() {
        SignupRequest req = new SignupRequest();
        req.setName("Khushi");
        req.setEmail("khushi@example.com");
        req.setPassword("123456");

        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        User saved = authService.signup(req);

        assertNotNull(saved);
        assertEquals(1L, saved.getId());
        assertEquals("Khushi", saved.getName());
        assertEquals("khushi@example.com", saved.getEmail());
        assertEquals("123456", saved.getPassword());
    }

    @Test
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setEmail("khushi@example.com");
        req.setPassword("123456");

        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setPassword("123456");

        when(userRepo.findByEmailAndPassword("khushi@example.com", "123456"))
                .thenReturn(Optional.of(user));

        User result = authService.login(req);

        assertEquals(1L, result.getId());
        assertEquals("Khushi", result.getName());
    }

    @Test
    void testLoginFailsForInvalidCredentials() {
        LoginRequest req = new LoginRequest();
        req.setEmail("wrong@example.com");
        req.setPassword("wrongpass");

        when(userRepo.findByEmailAndPassword("wrong@example.com", "wrongpass"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(req));

        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void testUpdateZelleInfo() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = authService.updateZelleInfo(1L, "zelle@example.com", "1234567890");

        assertEquals("zelle@example.com", updated.getZelleEmail());
        assertEquals("1234567890", updated.getZellePhone());
    }

    @Test
    void testUploadZelleQrSuccess() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        byte[] data = "qrdata".getBytes();

        authService.uploadZelleQr(1L, data, "zelle.png", "image/png");

        assertArrayEquals(data, user.getZelleQrData());
        assertEquals("zelle.png", user.getZelleQrFileName());
        assertEquals("image/png", user.getZelleQrContentType());
        assertTrue(user.getHasZelleQr());

        verify(userRepo).save(user);
    }

    @Test
    void testDeleteZelleQr() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setHasZelleQr(true);
        user.setZelleQrData("qrdata".getBytes());
        user.setZelleQrFileName("zelle.png");
        user.setZelleQrContentType("image/png");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        authService.deleteZelleQr(1L);

        assertNull(user.getZelleQrData());
        assertNull(user.getZelleQrFileName());
        assertNull(user.getZelleQrContentType());
        assertFalse(user.getHasZelleQr());

        verify(userRepo).save(user);
    }

    @Test
    void testGetZelleInfo() {
        User user = makeUser(1L, "Khushi", "khushi@example.com");
        user.setZelleEmail("zelle@example.com");
        user.setZellePhone("1234567890");
        user.setHasZelleQr(true);
        user.setZelleQrFileName("zelle.png");
        user.setZelleQrContentType("image/png");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        Map<String, Object> info = authService.getZelleInfo(1L);

        assertEquals(1L, info.get("userId"));
        assertEquals("Khushi", info.get("name"));
        assertEquals("khushi@example.com", info.get("email"));
        assertEquals("zelle@example.com", info.get("zelleEmail"));
        assertEquals("1234567890", info.get("zellePhone"));
        assertEquals(true, info.get("hasZelleQr"));
        assertEquals("zelle.png", info.get("zelleQrFileName"));
        assertEquals("image/png", info.get("zelleQrContentType"));
    }

    // =========================================================
    // BalanceService Tests
    // =========================================================

    @Test
    void testGetBalances() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(100L, 10L, 90.0, payer, "Dinner");

        ExpenseSplit split1 = new ExpenseSplit();
        split1.setExpense(expense);
        split1.setUserId(1L);
        split1.setAmount(30.0);

        ExpenseSplit split2 = new ExpenseSplit();
        split2.setExpense(expense);
        split2.setUserId(2L);
        split2.setAmount(30.0);

        ExpenseSplit split3 = new ExpenseSplit();
        split3.setExpense(expense);
        split3.setUserId(3L);
        split3.setAmount(30.0);

        when(expenseRepo.findByGroupId(10L)).thenReturn(List.of(expense));
        when(splitRepo.findByExpense(expense)).thenReturn(Arrays.asList(split1, split2, split3));

        Map<Long, Double> balances = balanceService.getBalances(10L);

        assertEquals(60.0, balances.get(1L), 0.001);
        assertEquals(-30.0, balances.get(2L), 0.001);
        assertEquals(-30.0, balances.get(3L), 0.001);
    }
 }



