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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
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
            if (req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body("Amount must be greater than zero.");
            }

            if (req.getParticipants() == null || req.getParticipants().isEmpty()) {
                return ResponseEntity.badRequest().body("Participants list cannot be empty.");
            }

            String splitType = req.getSplitType();
            if (splitType == null || splitType.trim().isEmpty()) {
                splitType = "EQUAL";
                req.setSplitType(splitType);
            }

            if ("PERCENTAGE".equalsIgnoreCase(splitType)) {
                if (req.getPercentages() == null || req.getPercentages().isEmpty()) {
                    return ResponseEntity.badRequest().body("Percentages map is required for PERCENTAGE split.");
                }

                Set<String> pctKeys = req.getPercentages().keySet();

                for (Long pid : req.getParticipants()) {
                    if (!pctKeys.contains(String.valueOf(pid))) {
                        return ResponseEntity.badRequest()
                                .body("Missing percentage for participant userId: " + pid);
                    }
                }

                for (String userIdStr : pctKeys) {
                    Long uid = Long.valueOf(userIdStr);
                    if (!req.getParticipants().contains(uid)) {
                        return ResponseEntity.badRequest()
                                .body("Percentages contains userId not in selected participants: " + uid);
                    }
                }

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

            Expense savedExpense = expenseService.addExpense(req);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Expense added successfully",
                    "expenseId", savedExpense.getId()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error adding expense: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable Long id) {
        try {
            Expense expense = expenseService.getExpenseById(id);
            return ResponseEntity.ok(expense);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

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

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Expense>> getExpensesByGroup(@PathVariable Long groupId) {
        try {
            List<Expense> expenses = expenseService.getExpensesByGroupId(groupId);
            return ResponseEntity.ok(expenses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<Map<Long, Double>> getUserBalances(@PathVariable Long userId) {
        try {
            Map<Long, Double> balances = expenseService.getUserBalances(userId);
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/activity/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity(@PathVariable Long userId) {
        try {
            List<Map<String, Object>> activity = expenseService.getRecentActivity(userId);
            return ResponseEntity.ok(activity);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editExpense(@PathVariable Long id, @RequestBody ExpenseRequest req) {
        try {
            if (req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Amount must be greater than zero."));
            }

            if (req.getParticipants() == null || req.getParticipants().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Participants list cannot be empty."));
            }

            String splitType = req.getSplitType();
            if (splitType == null || splitType.trim().isEmpty()) {
                splitType = "EQUAL";
                req.setSplitType(splitType);
            }

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
            return ResponseEntity.ok(Map.of(
                    "message", "Expense updated successfully",
                    "expenseId", id
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating expense: " + e.getMessage()));
        }
    }

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

    @GetMapping("/{id}/receipt")
    public ResponseEntity<?> downloadReceipt(@PathVariable Long id) {
        try {
            Expense expense = expenseService.getExpenseWithReceipt(id);

            if (expense.getReceiptData() == null || expense.getReceiptData().length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No receipt found for this expense."));
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

    // =========================
    // Sprint 3 Endpoints
    // =========================

    /**
     * GET: Generate automatic settlement instructions for a group.
     */
    @GetMapping("/group/{groupId}/settlement-plan")
    public ResponseEntity<?> getSettlementPlan(@PathVariable Long groupId) {
        try {
            List<Map<String, Object>> plan = expenseService.getSettlementPlanByGroup(groupId);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error generating settlement plan: " + e.getMessage()));
        }
    }

    /**
     * POST: Clear all debts for a group.
     * NOTE: In current implementation, this clears the group's expense ledger.
     */
    @PostMapping("/group/{groupId}/clear-debts")
    public ResponseEntity<?> clearAllDebts(@PathVariable Long groupId) {
        try {
            Map<String, Object> result = expenseService.clearAllDebtsForGroup(groupId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error clearing debts: " + e.getMessage()));
        }
    }

    /**
     * GET: Full balance sheet data for a group.
     */
    @GetMapping("/group/{groupId}/balance-sheet")
    public ResponseEntity<?> getBalanceSheet(@PathVariable Long groupId) {
        try {
            Map<String, Object> result = expenseService.getBalanceSheetData(groupId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching balance sheet: " + e.getMessage()));
        }
    }

    /**
     * GET: Export full balance sheet as CSV.
     */
    @GetMapping("/group/{groupId}/export/csv")
    public ResponseEntity<?> exportBalanceSheetCsv(@PathVariable Long groupId) {
        try {
            String csv = expenseService.exportBalanceSheetCsv(groupId);
            ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8));

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"group_" + groupId + "_balance_sheet.csv\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error exporting CSV: " + e.getMessage()));
        }
    }
}