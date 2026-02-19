package com.fairshare.controller;

import com.fairshare.model.Expense;
import com.fairshare.service.ExpenseService;
import com.fairshare.payload.ExpenseRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") 
public class ExpenseController {

    @Autowired 
    private ExpenseService expenseService;

    /**
     * POST: Creates a new expense and splits it among participants.
     */
    @PostMapping("/add")
    public ResponseEntity<?> addExpense(@RequestBody ExpenseRequest req) {
        try {
            if (req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body("Amount must be greater than zero.");
            }
            if (req.getParticipants() == null || req.getParticipants().isEmpty()) {
                return ResponseEntity.badRequest().body("Participants list cannot be empty.");
            }
            
            expenseService.addExpense(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Expense added successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error adding expense: " + e.getMessage()));
        }
    }

    /**
     * GET: Fetches a single expense by ID.
     * Required for pre-filling the Edit form in EditExpense.jsx.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable Long id) {
        try {
            Expense expense = expenseService.getExpenseById(id);
            return ResponseEntity.ok(expense);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * GET: Fetches all expenses for a specific group.
     * This is what populates the history in GroupDetails.jsx.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Expense>> getExpensesByGroup(@PathVariable Long groupId) {
        try {
            List<Expense> expenses = expenseService.getExpensesByGroupId(groupId);
            return ResponseEntity.ok(expenses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET: Returns Map of Group ID to Balance.
     */
    @GetMapping("/balance/{userId}")
    public ResponseEntity<Map<Long, Double>> getUserBalances(@PathVariable Long userId) {
        try {
            Map<Long, Double> balances = expenseService.getUserBalances(userId);
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET: Fetches a list of recent transactions for the user dashboard.
     */
    @GetMapping("/activity/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity(@PathVariable Long userId) {
        try {
            List<Map<String, Object>> activity = expenseService.getRecentActivity(userId);
            return ResponseEntity.ok(activity);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * PUT: Updates an existing expense by ID.
     */
    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editExpense(@PathVariable Long id, @RequestBody ExpenseRequest req) {
        try {
            expenseService.editExpense(id, req);
            return ResponseEntity.ok(Map.of("message", "Expense updated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating expense: " + e.getMessage()));
        }
    }

    /**
     * DELETE: Deletes an expense and its associated splits.
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable Long id) {
        try {
            expenseService.deleteExpense(id);
            return ResponseEntity.ok(Map.of("message", "Expense deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deleting expense: " + e.getMessage()));
        }
    }
}