package com.fairshare.controller;

import com.fairshare.model.Expense;
import com.fairshare.service.ExpenseService;
import com.fairshare.payload.ExpenseRequest;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

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
            // --- Basic validations (existing) ---
            if (req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body("Amount must be greater than zero.");
            }
            if (req.getParticipants() == null || req.getParticipants().isEmpty()) {
                return ResponseEntity.badRequest().body("Participants list cannot be empty.");
            }

            // --- Sprint 2: Default splitType to EQUAL if missing (keeps old behavior working) ---
            String splitType = req.getSplitType();
            if (splitType == null || splitType.trim().isEmpty()) {
                splitType = "EQUAL";
                req.setSplitType(splitType);
            }

            // --- Sprint 2: Validate percentage split input ---
            if ("PERCENTAGE".equalsIgnoreCase(splitType)) {
                if (req.getPercentages() == null || req.getPercentages().isEmpty()) {
                    return ResponseEntity.badRequest().body("Percentages map is required for PERCENTAGE split.");
                }

                // Ensure only selected participants are included and sum is 100
                Set<String> pctKeys = req.getPercentages().keySet();
                for (Long pid : req.getParticipants()) {
                    if (!pctKeys.contains(String.valueOf(pid))) {
                        return ResponseEntity.badRequest()
                                .body("Missing percentage for participant userId: " + pid);
                    }
                }

                // If percentages contains extra users not in participants, reject
                for (String userIdStr : pctKeys) {
                    Long uid = Long.valueOf(userIdStr);
                    if (!req.getParticipants().contains(uid)) {
                        return ResponseEntity.badRequest()
                                .body("Percentages contains userId not in selected participants: " + uid);
                    }
                }

                // Sum must be 100 (with tiny tolerance)
                double sum = 0.0;
                for (Map.Entry<String, Double> entry : req.getPercentages().entrySet()) {
                    Double val = entry.getValue();
                    if (val == null || val < 0) {
                        return ResponseEntity.badRequest()
                                .body("Invalid percentage value for userId " + entry.getKey());
                    }
                    sum += val;
                }

                if (Math.abs(sum - 100.0) > 0.0001) {
                    return ResponseEntity.badRequest()
                            .body("Percentages must sum to 100. Current sum: " + sum);
                }
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
     * Sprint 2 (Helpful for Edit):
     * GET: Fetch splits + splitType info for an expense.
     */
    @GetMapping("/{id}/splits")
    public ResponseEntity<?> getExpenseSplits(@PathVariable Long id) {
        try {
            Map<String, Object> data = expenseService.getExpenseSplitsForEdit(id);
            return ResponseEntity.ok(data);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching expense splits: " + e.getMessage()));
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
            // --- Basic validations (same as add) ---
            if (req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Amount must be greater than zero."));
            }
            if (req.getParticipants() == null || req.getParticipants().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Participants list cannot be empty."));
            }

            // Default splitType for backward compatibility
            String splitType = req.getSplitType();
            if (splitType == null || splitType.trim().isEmpty()) {
                splitType = "EQUAL";
                req.setSplitType(splitType);
            }

            // Validate percentage split for edits too
            if ("PERCENTAGE".equalsIgnoreCase(splitType)) {
                if (req.getPercentages() == null || req.getPercentages().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Percentages map is required for PERCENTAGE split."));
                }

                Set<String> pctKeys = req.getPercentages().keySet();

                for (Long pid : req.getParticipants()) {
                    if (!pctKeys.contains(String.valueOf(pid))) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Missing percentage for participant userId: " + pid));
                    }
                }

                for (String userIdStr : pctKeys) {
                    Long uid = Long.valueOf(userIdStr);
                    if (!req.getParticipants().contains(uid)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Percentages contains userId not in selected participants: " + uid));
                    }
                }

                double sum = 0.0;
                for (Map.Entry<String, Double> entry : req.getPercentages().entrySet()) {
                    Double val = entry.getValue();
                    if (val == null || val < 0) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Invalid percentage value for userId " + entry.getKey()));
                    }
                    sum += val;
                }

                if (Math.abs(sum - 100.0) > 0.0001) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Percentages must sum to 100. Current sum: " + sum));
                }
            }

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

    // ============================================================
    // NEW: Receipt Upload / View (added without changing existing)
    // ============================================================

    /**
     * POST: Upload (or replace) a receipt for an expense.
     * Form-Data:
     *   key = file, value = (image/pdf)
     */
    @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadReceipt(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Receipt file is required."));
            }

            String contentType = file.getContentType();
            String originalName = file.getOriginalFilename();

            expenseService.uploadReceipt(id, file.getBytes(), originalName, contentType);

            // return metadata helpful for UI
            return ResponseEntity.ok(Map.of(
                    "message", "Receipt uploaded successfully",
                    "expenseId", id,
                    "fileName", originalName,
                    "contentType", contentType
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error uploading receipt: " + e.getMessage()));
        }
    }

    /**
     * GET: Receipt metadata only (no bytes)
     * Useful to show "View Receipt" button.
     */
    @GetMapping("/{id}/receipt/info")
    public ResponseEntity<?> getReceiptInfo(@PathVariable Long id) {
        try {
            Map<String, Object> info = expenseService.getReceiptInfo(id);
            return ResponseEntity.ok(info);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching receipt info: " + e.getMessage()));
        }
    }

    /**
     * GET: Download / view the receipt bytes.
     * Returns:
     *  - Content-Type = stored contentType (image/* or application/pdf)
     *  - Content-Disposition = inline (browser preview) with filename
     */
    @GetMapping("/{id}/receipt")
    public ResponseEntity<?> downloadReceipt(@PathVariable Long id) {
        try {
            Expense expense = expenseService.getExpenseWithReceipt(id);

            if (expense.getReceiptData() == null || expense.getReceiptData().length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No receipt found for this expense."));
            }

            String contentType = expense.getReceiptContentType() != null
                    ? expense.getReceiptContentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            String fileName = (expense.getReceiptFileName() != null && !expense.getReceiptFileName().isBlank())
                    ? expense.getReceiptFileName()
                    : ("expense_" + id + "_receipt");

            ByteArrayResource resource = new ByteArrayResource(expense.getReceiptData());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error downloading receipt: " + e.getMessage()));
        }
    }

    /**
     * DELETE: Remove receipt from an expense.
     */
    @DeleteMapping("/{id}/receipt")
    public ResponseEntity<?> deleteReceipt(@PathVariable Long id) {
        try {
            expenseService.deleteReceipt(id);
            return ResponseEntity.ok(Map.of("message", "Receipt deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deleting receipt: " + e.getMessage()));
        }
    }
}