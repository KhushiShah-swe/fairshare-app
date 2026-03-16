package com.fairshare;

import com.fairshare.controller.ExpenseController;
import com.fairshare.model.Expense;
import com.fairshare.model.User;
import com.fairshare.payload.ExpenseRequest;
import com.fairshare.service.ExpenseService;
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

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpenseControllerTests {

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExpenseController expenseController;

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

    // =========================================================
    // addExpense
    // =========================================================

    @Test
    void testAddExpenseSuccess() {
        ExpenseRequest req = makeEqualRequest();
        Expense saved = new Expense();
        saved.setId(500L);

        when(expenseService.addExpense(req)).thenReturn(saved);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense added successfully", body.get("message"));
        assertEquals(500L, body.get("expenseId"));
        verify(expenseService).addExpense(req);
    }

    @Test
    void testAddExpenseFailsForInvalidAmount() {
        ExpenseRequest req = makeEqualRequest();
        req.setAmount(0.0);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Amount must be greater than zero.", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpenseFailsForEmptyParticipants() {
        ExpenseRequest req = makeEqualRequest();
        req.setParticipants(new ArrayList<>());

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Participants list cannot be empty.", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpenseDefaultsSplitTypeToEqual() {
        ExpenseRequest req = makeEqualRequest();
        req.setSplitType(null);

        Expense saved = new Expense();
        saved.setId(777L);

        when(expenseService.addExpense(req)).thenReturn(saved);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("EQUAL", req.getSplitType());
        verify(expenseService).addExpense(req);
    }

    @Test
    void testAddExpensePercentageFailsWhenPercentagesMissing() {
        ExpenseRequest req = makePercentageRequest();
        req.setPercentages(null);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Percentages map is required for PERCENTAGE split.", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpensePercentageFailsWhenParticipantPercentageMissing() {
        ExpenseRequest req = makePercentageRequest();
        req.getPercentages().remove("2");

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing percentage for participant userId: 2", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpensePercentageFailsWhenExtraUserInPercentageMap() {
        ExpenseRequest req = makePercentageRequest();
        req.getPercentages().put("99", 0.0);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Percentages contains userId not in selected participants: 99", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpensePercentageFailsWhenNegativePercentage() {
        ExpenseRequest req = makePercentageRequest();
        req.getPercentages().put("1", -10.0);
        req.getPercentages().put("2", 110.0);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid percentage value for userId 1", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpensePercentageFailsWhenPercentagesDoNotSumTo100() {
        ExpenseRequest req = makePercentageRequest();
        req.getPercentages().put("1", 20.0);
        req.getPercentages().put("2", 50.0);

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Percentages must sum to 100. Current sum: 70.0", response.getBody());
        verify(expenseService, never()).addExpense(any());
    }

    @Test
    void testAddExpenseReturnsInternalServerErrorWhenServiceThrows() {
        ExpenseRequest req = makeEqualRequest();

        when(expenseService.addExpense(req)).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<?> response = expenseController.addExpense(req);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertTrue(String.valueOf(body.get("error")).contains("Error adding expense: DB error"));
    }

    // =========================================================
    // getExpenseById
    // =========================================================

    @Test
    void testGetExpenseByIdSuccess() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(101L, 10L, 90.0, payer, "Pizza");

        when(expenseService.getExpenseById(101L)).thenReturn(expense);

        ResponseEntity<Expense> response = expenseController.getExpenseById(101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expense, response.getBody());
    }

    @Test
    void testGetExpenseByIdNotFound() {
        when(expenseService.getExpenseById(999L)).thenThrow(new RuntimeException("Expense not found"));

        ResponseEntity<Expense> response = expenseController.getExpenseById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    // =========================================================
    // getExpenseSplits
    // =========================================================

    @Test
    void testGetExpenseSplitsSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("expenseId", 5L);
        data.put("splitType", "EQUAL");
        data.put("participants", Arrays.asList(1L, 2L, 3L));

        when(expenseService.getExpenseSplitsForEdit(5L)).thenReturn(data);

        ResponseEntity<?> response = expenseController.getExpenseSplits(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(data, response.getBody());
    }

    @Test
    void testGetExpenseSplitsNotFound() {
        when(expenseService.getExpenseSplitsForEdit(5L)).thenThrow(new RuntimeException("Expense not found"));

        ResponseEntity<?> response = expenseController.getExpenseSplits(5L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense not found", body.get("error"));
    }

    // =========================================================
    // group expenses / balances / activity
    // =========================================================

    @Test
    void testGetExpensesByGroupSuccess() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(101L, 10L, 90.0, payer, "Pizza");

        when(expenseService.getExpensesByGroupId(10L)).thenReturn(List.of(expense));

        ResponseEntity<List<Expense>> response = expenseController.getExpensesByGroup(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetExpensesByGroupFailure() {
        when(expenseService.getExpensesByGroupId(10L)).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<List<Expense>> response = expenseController.getExpensesByGroup(10L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetUserBalancesSuccess() {
        Map<Long, Double> balances = new HashMap<>();
        balances.put(10L, 50.0);

        when(expenseService.getUserBalances(1L)).thenReturn(balances);

        ResponseEntity<Map<Long, Double>> response = expenseController.getUserBalances(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(balances, response.getBody());
    }

    @Test
    void testGetRecentActivitySuccess() {
        Map<String, Object> activityItem = new HashMap<>();
        activityItem.put("id", 1L);
        activityItem.put("desc", "Pizza Dinner");

        when(expenseService.getRecentActivity(1L)).thenReturn(List.of(activityItem));

        ResponseEntity<List<Map<String, Object>>> response = expenseController.getRecentActivity(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Pizza Dinner", response.getBody().get(0).get("desc"));
    }

    // =========================================================
    // editExpense
    // =========================================================

    @Test
    void testEditExpenseSuccess() {
        ExpenseRequest req = makeEqualRequest();

        ResponseEntity<?> response = expenseController.editExpense(50L, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense updated successfully", body.get("message"));
        assertEquals(50L, body.get("expenseId"));

        verify(expenseService).editExpense(50L, req);
    }

    @Test
    void testEditExpenseFailsForInvalidAmount() {
        ExpenseRequest req = makeEqualRequest();
        req.setAmount(-5.0);

        ResponseEntity<?> response = expenseController.editExpense(50L, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Amount must be greater than zero.", body.get("error"));
        verify(expenseService, never()).editExpense(anyLong(), any());
    }

    @Test
    void testEditExpenseFailsWhenParticipantsEmpty() {
        ExpenseRequest req = makeEqualRequest();
        req.setParticipants(new ArrayList<>());

        ResponseEntity<?> response = expenseController.editExpense(50L, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Participants list cannot be empty.", body.get("error"));
        verify(expenseService, never()).editExpense(anyLong(), any());
    }

    @Test
    void testEditExpensePercentageFailsWhenPercentagesMissing() {
        ExpenseRequest req = makePercentageRequest();
        req.setPercentages(null);

        ResponseEntity<?> response = expenseController.editExpense(50L, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Percentages map is required for PERCENTAGE split.", body.get("error"));
    }

    @Test
    void testEditExpenseNotFound() {
        ExpenseRequest req = makeEqualRequest();
        doThrow(new RuntimeException("Expense not found")).when(expenseService).editExpense(50L, req);

        ResponseEntity<?> response = expenseController.editExpense(50L, req);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense not found", body.get("error"));
    }

    // =========================================================
    // deleteExpense
    // =========================================================

    @Test
    void testDeleteExpenseSuccess() {
        ResponseEntity<?> response = expenseController.deleteExpense(200L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense deleted successfully", body.get("message"));
        verify(expenseService).deleteExpense(200L);
    }

    @Test
    void testDeleteExpenseNotFound() {
        doThrow(new RuntimeException("Expense not found")).when(expenseService).deleteExpense(200L);

        ResponseEntity<?> response = expenseController.deleteExpense(200L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense not found", body.get("error"));
    }

    // =========================================================
    // receipt endpoints
    // =========================================================

    @Test
    void testUploadReceiptSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.jpg",
                "image/jpeg",
                "fake-image-data".getBytes()
        );

        ResponseEntity<?> response = expenseController.uploadReceipt(200L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Receipt uploaded successfully", body.get("message"));
        assertEquals(200L, body.get("expenseId"));
        assertEquals("receipt.jpg", body.get("fileName"));
        assertEquals("image/jpeg", body.get("contentType"));

        verify(expenseService).uploadReceipt(200L, file.getBytes(), "receipt.jpg", "image/jpeg");
    }

    @Test
    void testUploadReceiptFailsWhenFileMissing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "image/jpeg",
                new byte[0]
        );

        ResponseEntity<?> response = expenseController.uploadReceipt(200L, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Receipt file is required.", body.get("error"));
    }

    @Test
    void testUploadReceiptBadRequestFromService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.exe",
                "application/octet-stream",
                "bad".getBytes()
        );

        doThrow(new RuntimeException("Only image/* or application/pdf receipts are allowed."))
                .when(expenseService)
                .uploadReceipt(200L, file.getBytes(), "receipt.exe", "application/octet-stream");

        ResponseEntity<?> response = expenseController.uploadReceipt(200L, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Only image/* or application/pdf receipts are allowed.", body.get("error"));
    }

    @Test
    void testGetReceiptInfoSuccess() {
        Map<String, Object> info = new HashMap<>();
        info.put("expenseId", 201L);
        info.put("hasReceipt", true);
        info.put("fileName", "taxi.pdf");
        info.put("contentType", "application/pdf");

        when(expenseService.getReceiptInfo(201L)).thenReturn(info);

        ResponseEntity<?> response = expenseController.getReceiptInfo(201L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(info, response.getBody());
    }

    @Test
    void testGetReceiptInfoNotFound() {
        when(expenseService.getReceiptInfo(201L)).thenThrow(new RuntimeException("Expense not found"));

        ResponseEntity<?> response = expenseController.getReceiptInfo(201L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense not found", body.get("error"));
    }

    @Test
    void testDownloadReceiptSuccess() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(300L, 10L, 40.0, payer, "Cab");
        expense.setReceiptData("pdf-data".getBytes());
        expense.setReceiptFileName("cab.pdf");
        expense.setReceiptContentType("application/pdf");

        when(expenseService.getExpenseWithReceipt(300L)).thenReturn(expense);

        ResponseEntity<?> response = expenseController.downloadReceipt(300L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ByteArrayResource);
        assertEquals("inline; filename=\"cab.pdf\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void testDownloadReceiptNotFoundWhenNoData() {
        User payer = makeUser(1L, "Khushi", "khushi@example.com");
        Expense expense = makeExpense(300L, 10L, 40.0, payer, "Cab");
        expense.setReceiptData(null);

        when(expenseService.getExpenseWithReceipt(300L)).thenReturn(expense);

        ResponseEntity<?> response = expenseController.downloadReceipt(300L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("No receipt found for this expense.", body.get("error"));
    }

    @Test
    void testDeleteReceiptSuccess() {
        ResponseEntity<?> response = expenseController.deleteReceipt(300L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Receipt deleted successfully", body.get("message"));
        verify(expenseService).deleteReceipt(300L);
    }

    @Test
    void testDeleteReceiptNotFound() {
        doThrow(new RuntimeException("Expense not found")).when(expenseService).deleteReceipt(300L);

        ResponseEntity<?> response = expenseController.deleteReceipt(300L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals("Expense not found", body.get("error"));
    }

    // =========================================================
    // Sprint 3 endpoints
    // =========================================================

    @Test
    void testGetSettlementPlanSuccess() {
        Map<String, Object> item = new HashMap<>();
        item.put("fromName", "Bob");
        item.put("toName", "Alice");
        item.put("amount", 30.0);
        item.put("text", "Bob pays Alice $30.00");

        when(expenseService.getSettlementPlanByGroup(10L)).thenReturn(List.of(item));

        ResponseEntity<?> response = expenseController.getSettlementPlan(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();

        assertEquals(1, body.size());
        assertEquals("Bob pays Alice $30.00", body.get(0).get("text"));
    }

    @Test
    void testGetSettlementPlanFailure() {
        when(expenseService.getSettlementPlanByGroup(10L)).thenThrow(new RuntimeException("Failed"));

        ResponseEntity<?> response = expenseController.getSettlementPlan(10L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertTrue(String.valueOf(body.get("error")).contains("Error generating settlement plan"));
    }

    @Test
    void testClearAllDebtsSuccess() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "All debts cleared successfully.");
        result.put("groupId", 10L);
        result.put("clearedExpenses", 2);

        when(expenseService.clearAllDebtsForGroup(10L)).thenReturn(result);

        ResponseEntity<?> response = expenseController.clearAllDebts(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void testGetBalanceSheetSuccess() {
        Map<String, Object> result = new HashMap<>();
        result.put("groupId", 10L);
        result.put("totalExpenses", 100.0);
        result.put("expenses", new ArrayList<>());
        result.put("balances", new ArrayList<>());
        result.put("settlementPlan", new ArrayList<>());

        when(expenseService.getBalanceSheetData(10L)).thenReturn(result);

        ResponseEntity<?> response = expenseController.getBalanceSheet(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void testExportBalanceSheetCsvSuccess() {
        String csv = "FAIRSHARE BALANCE SHEET\nGroup ID,10\n";

        when(expenseService.exportBalanceSheetCsv(10L)).thenReturn(csv);

        ResponseEntity<?> response = expenseController.exportBalanceSheetCsv(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ByteArrayResource);
        assertEquals("attachment; filename=\"group_10_balance_sheet.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }
}