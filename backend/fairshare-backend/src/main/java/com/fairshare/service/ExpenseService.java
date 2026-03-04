package com.fairshare.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
import com.fairshare.model.User;
import com.fairshare.payload.ExpenseRequest;
import com.fairshare.repository.ExpenseRepository;
import com.fairshare.repository.ExpenseSplitRepository;
import com.fairshare.repository.UserRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepo;
    @Autowired private ExpenseSplitRepository splitRepo;
    @Autowired private UserRepository userRepo;

    public Expense getExpenseById(Long id) {
        return expenseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
    }

    public List<Expense> getExpensesByGroupId(Long groupId) {
        return expenseRepo.findByGroupIdOrderByIdDesc(groupId);
    }

    @Transactional
    public void addExpense(ExpenseRequest req) {
        Expense e = new Expense();
        mapRequestToEntity(req, e);
        e.setGroupId(req.getGroupId());

        Expense savedExpense = expenseRepo.save(e);

        String splitType = normalizeSplitType(req.getSplitType());
        saveSplits(savedExpense, req.getParticipants(), req.getAmount(), splitType, req.getPercentages());
    }

    @Transactional
    public void editExpense(Long id, ExpenseRequest req) {
        Expense e = expenseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        mapRequestToEntity(req, e);
        expenseRepo.save(e);

        splitRepo.deleteByExpenseId(id);

        String splitType = normalizeSplitType(req.getSplitType());
        saveSplits(e, req.getParticipants(), req.getAmount(), splitType, req.getPercentages());
    }

    @Transactional
    public void deleteExpense(Long id) {
        splitRepo.deleteByExpenseId(id);
        expenseRepo.deleteById(id);
    }

    // =========================
    // Receipt Upload (NEW)
    // =========================

    /**
     * Attach/replace receipt for an expense (PDF/Image).
     * Call this from a controller endpoint like:
     * POST /api/expenses/{id}/receipt  (multipart/form-data file=...)
     */
    @Transactional
    public void uploadReceipt(Long expenseId, byte[] fileBytes, String originalFileName, String contentType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("Receipt file is empty.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new RuntimeException("Receipt contentType is required.");
        }

        // Accept only image/* or application/pdf
        boolean isImage = contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        boolean isPdf = contentType.equalsIgnoreCase("application/pdf");
        if (!isImage && !isPdf) {
            throw new RuntimeException("Only image/* or application/pdf receipts are allowed.");
        }

        Expense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));

        expense.setReceiptData(fileBytes);
        expense.setReceiptFileName(originalFileName);
        expense.setReceiptContentType(contentType);
        expense.setHasReceipt(true);

        expenseRepo.save(expense);
    }

    /**
     * Returns receipt metadata (no bytes) for UI buttons.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReceiptInfo(Long expenseId) {
        Expense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));

        Map<String, Object> info = new HashMap<>();
        info.put("expenseId", expense.getId());
        info.put("hasReceipt", Boolean.TRUE.equals(expense.getHasReceipt()));
        info.put("fileName", expense.getReceiptFileName());
        info.put("contentType", expense.getReceiptContentType());
        return info;
    }

    /**
     * Returns receipt bytes + contentType for download/view.
     * Controller should write bytes to response with correct Content-Type.
     */
    @Transactional(readOnly = true)
    public Expense getExpenseWithReceipt(Long expenseId) {
        return expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));
    }

    /**
     * Optional: remove receipt from an expense.
     */
    @Transactional
    public void deleteReceipt(Long expenseId) {
        Expense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));

        expense.setReceiptData(null);
        expense.setReceiptFileName(null);
        expense.setReceiptContentType(null);
        expense.setHasReceipt(false);

        expenseRepo.save(expense);
    }

    // =========================
    // Existing code (unchanged)
    // =========================

    private void mapRequestToEntity(ExpenseRequest req, Expense e) {
        e.setAmount(req.getAmount());
        e.setDescription(req.getDescription());

        // ✅ FIX: Expense.setPaidBy expects User, but request has Long userId
        if (req.getPaidBy() == null) {
            throw new RuntimeException("paidBy is required.");
        }
        User payer = userRepo.findById(req.getPaidBy())
                .orElseThrow(() -> new RuntimeException("User not found for id: " + req.getPaidBy()));
        e.setPaidBy(payer);

        e.setCategory(req.getCategory());
        e.setNotes(req.getNotes());

        if (req.getExpenseDate() != null && !req.getExpenseDate().isEmpty()) {
            e.setExpenseDate(LocalDate.parse(req.getExpenseDate()));
        } else {
            e.setExpenseDate(LocalDate.now());
        }
    }

    private String normalizeSplitType(String splitType) {
        if (splitType == null || splitType.trim().isEmpty()) return "EQUAL";
        String st = splitType.trim().toUpperCase(Locale.ROOT);
        if (!st.equals("EQUAL") && !st.equals("PERCENTAGE")) return "EQUAL";
        return st;
    }

    private void saveSplits(
            Expense expense,
            List<Long> participants,
            double totalAmount,
            String splitType,
            Map<String, Double> percentages
    ) {
        if (participants == null || participants.isEmpty()) {
            throw new RuntimeException("Expense must have at least one participant.");
        }

        if ("PERCENTAGE".equals(splitType)) {
            if (percentages == null || percentages.isEmpty()) {
                throw new RuntimeException("Percentages are required for PERCENTAGE split.");
            }

            Set<String> pctKeys = percentages.keySet();

            for (Long uid : participants) {
                if (!pctKeys.contains(String.valueOf(uid))) {
                    throw new RuntimeException("Missing percentage for participant userId: " + uid);
                }
            }

            for (String uidStr : pctKeys) {
                Long uid = Long.valueOf(uidStr);
                if (!participants.contains(uid)) {
                    throw new RuntimeException("Percentages contains userId not in selected participants: " + uid);
                }
            }

            double sum = 0.0;
            for (Map.Entry<String, Double> entry : percentages.entrySet()) {
                Double val = entry.getValue();
                if (val == null || val < 0) {
                    throw new RuntimeException("Invalid percentage value for userId " + entry.getKey());
                }
                sum += val;
            }
            if (Math.abs(sum - 100.0) > 0.0001) {
                throw new RuntimeException("Percentages must sum to 100. Current sum: " + sum);
            }

            for (Long uid : participants) {
                double pct = percentages.get(String.valueOf(uid));
                double amount = totalAmount * (pct / 100.0);

                ExpenseSplit es = new ExpenseSplit();
                es.setExpense(expense);
                es.setUserId(uid);         // ✅ ExpenseSplit stores Long userId
                es.setPercentage(pct);
                es.setAmount(amount);
                splitRepo.save(es);
            }

        } else {
            double splitAmount = totalAmount / participants.size();

            for (Long uid : participants) {
                ExpenseSplit es = new ExpenseSplit();
                es.setExpense(expense);
                es.setUserId(uid);         // ✅ ExpenseSplit stores Long userId
                es.setPercentage(null);
                es.setAmount(splitAmount);
                splitRepo.save(es);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getExpenseSplitsForEdit(Long expenseId) {
        Expense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        List<ExpenseSplit> splits = splitRepo.findByExpenseId(expenseId);

        boolean anyPercentage = splits.stream().anyMatch(s -> s.getPercentage() != null);
        String splitType = anyPercentage ? "PERCENTAGE" : "EQUAL";

        List<Long> participants = splits.stream()
                .map(ExpenseSplit::getUserId)
                .collect(Collectors.toList());

        Map<String, Double> percentages = new HashMap<>();
        if (anyPercentage) {
            for (ExpenseSplit s : splits) {
                if (s.getPercentage() != null) {
                    percentages.put(String.valueOf(s.getUserId()), s.getPercentage());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("expenseId", expense.getId());
        result.put("splitType", splitType);
        result.put("participants", participants);
        result.put("percentages", percentages);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> getUserBalances(Long userId) {
        List<ExpenseSplit> userSplits = splitRepo.findByUserId(userId);
        Map<Long, Double> groupBalances = new HashMap<>();

        for (ExpenseSplit split : userSplits) {
            Expense expense = split.getExpense();
            if (expense == null) continue;

            Long paidById = expense.getPaidBy() != null ? expense.getPaidBy().getId() : null;
            if (paidById == null) continue;

            double effect = paidById.equals(userId)
                    ? (expense.getAmount() - split.getAmount())
                    : -split.getAmount();

            Long gId = expense.getGroupId();
            groupBalances.put(gId, groupBalances.getOrDefault(gId, 0.0) + effect);
        }
        return groupBalances;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentActivity(Long userId) {
        return splitRepo.findByUserId(userId).stream()
                .map(split -> {
                    Expense e = split.getExpense();
                    if (e == null) return null;

                    Long paidById = e.getPaidBy() != null ? e.getPaidBy().getId() : null;

                    Map<String, Object> activity = new HashMap<>();
                    activity.put("id", e.getId());
                    activity.put("desc", e.getDescription());
                    activity.put("amount", e.getAmount());
                    activity.put("category", e.getCategory());
                    activity.put("date", e.getExpenseDate());
                    activity.put("payer", paidById != null && paidById.equals(userId) ? "You" : "User " + paidById);
                    return activity;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}