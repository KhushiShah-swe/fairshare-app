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
    public Expense addExpense(ExpenseRequest req) {
        Expense e = new Expense();
        mapRequestToEntity(req, e);
        e.setGroupId(req.getGroupId());

        Expense savedExpense = expenseRepo.save(e);

        String splitType = normalizeSplitType(req.getSplitType());
        saveSplits(savedExpense, req.getParticipants(), req.getAmount(), splitType, req.getPercentages());

        return savedExpense;
    }

    @Transactional
    public void editExpense(Long id, ExpenseRequest req) {
        Expense e = expenseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        mapRequestToEntity(req, e);
        e.setGroupId(req.getGroupId());
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
    // Receipt Upload
    // =========================

    @Transactional
    public void uploadReceipt(Long expenseId, byte[] fileBytes, String originalFileName, String contentType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("Receipt file is empty.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new RuntimeException("Receipt contentType is required.");
        }

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

    @Transactional(readOnly = true)
    public Expense getExpenseWithReceipt(Long expenseId) {
        return expenseRepo.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + expenseId));
    }

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
    // Sprint 3: Settlement Plan + Balance Sheet + Clear Debts
    // =========================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSettlementPlanByGroup(Long groupId) {
        List<Map<String, Object>> directDebts = calculateDirectDebts(groupId);

        Map<String, Double> merged = new LinkedHashMap<>();

        for (Map<String, Object> debt : directDebts) {
            Long fromUserId = ((Number) debt.get("fromUserId")).longValue();
            Long toUserId = ((Number) debt.get("toUserId")).longValue();
            double amount = round2(((Number) debt.get("amount")).doubleValue());

            if (amount <= 0.009) continue;

            String key = fromUserId + "->" + toUserId;
            merged.put(key, round2(merged.getOrDefault(key, 0.0) + amount));
        }

        List<Map<String, Object>> instructions = new ArrayList<>();

        for (Map.Entry<String, Double> entry : merged.entrySet()) {
            double amount = round2(entry.getValue());
            if (amount <= 0.009) continue;

            String[] parts = entry.getKey().split("->");
            Long fromUserId = Long.valueOf(parts[0]);
            Long toUserId = Long.valueOf(parts[1]);

            Map<String, Object> instruction = new HashMap<>();
            instruction.put("fromUserId", fromUserId);
            instruction.put("fromName", getUserDisplayName(fromUserId));
            instruction.put("toUserId", toUserId);
            instruction.put("toName", getUserDisplayName(toUserId));
            instruction.put("amount", amount);
            instruction.put("text", getUserDisplayName(fromUserId) + " pays " + getUserDisplayName(toUserId) + " $" + String.format("%.2f", amount));
            instructions.add(instruction);
        }

        instructions.sort((a, b) -> Double.compare(
                ((Number) b.get("amount")).doubleValue(),
                ((Number) a.get("amount")).doubleValue()
        ));

        return instructions;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceSheetData(Long groupId) {
        List<Expense> expenses = expenseRepo.findByGroupIdOrderByIdDesc(groupId);
        Map<Long, Double> balances = calculateGroupBalances(groupId);
        List<Map<String, Object>> settlementPlan = getSettlementPlanByGroup(groupId);

        List<Map<String, Object>> expenseRows = new ArrayList<>();
        for (Expense e : expenses) {
            Map<String, Object> row = new HashMap<>();
            row.put("expenseId", e.getId());
            row.put("date", e.getExpenseDate());
            row.put("description", e.getDescription());
            row.put("category", e.getCategory());
            row.put("amount", round2(e.getAmount()));
            row.put("paidById", e.getPaidBy() != null ? e.getPaidBy().getId() : null);
            row.put("paidByName", e.getPaidBy() != null ? getUserDisplayName(e.getPaidBy().getId()) : "Unknown");

            List<ExpenseSplit> splits = splitRepo.findByExpenseId(e.getId());
            List<Map<String, Object>> splitRows = new ArrayList<>();
            for (ExpenseSplit s : splits) {
                Map<String, Object> splitRow = new HashMap<>();
                splitRow.put("userId", s.getUserId());
                splitRow.put("name", getUserDisplayName(s.getUserId()));
                splitRow.put("amount", round2(s.getAmount()));
                splitRow.put("percentage", s.getPercentage());
                splitRows.add(splitRow);
            }

            row.put("splits", splitRows);
            expenseRows.add(row);
        }

        List<Map<String, Object>> balanceRows = balances.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", entry.getKey());
                    row.put("name", getUserDisplayName(entry.getKey()));
                    row.put("netBalance", round2(entry.getValue()));
                    return row;
                })
                .sorted((a, b) -> Double.compare(
                        ((Number) b.get("netBalance")).doubleValue(),
                        ((Number) a.get("netBalance")).doubleValue()
                ))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("groupId", groupId);
        result.put("expenses", expenseRows);
        result.put("balances", balanceRows);
        result.put("settlementPlan", settlementPlan);
        result.put("totalExpenses", round2(expenses.stream().mapToDouble(Expense::getAmount).sum()));
        return result;
    }

    @Transactional(readOnly = true)
    public String exportBalanceSheetCsv(Long groupId) {
        Map<String, Object> data = getBalanceSheetData(groupId);

        StringBuilder sb = new StringBuilder();

        sb.append("FAIRSHARE BALANCE SHEET\n");
        sb.append("Group ID,").append(groupId).append("\n\n");

        sb.append("EXPENSES\n");
        sb.append("Expense ID,Date,Description,Category,Amount,Paid By\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expenses = (List<Map<String, Object>>) data.get("expenses");
        for (Map<String, Object> e : expenses) {
            sb.append(csv(e.get("expenseId"))).append(",");
            sb.append(csv(e.get("date"))).append(",");
            sb.append(csv(e.get("description"))).append(",");
            sb.append(csv(e.get("category"))).append(",");
            sb.append(csv(e.get("amount"))).append(",");
            sb.append(csv(e.get("paidByName"))).append("\n");
        }

        sb.append("\nBALANCES\n");
        sb.append("User ID,Name,Net Balance\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> balances = (List<Map<String, Object>>) data.get("balances");
        for (Map<String, Object> b : balances) {
            sb.append(csv(b.get("userId"))).append(",");
            sb.append(csv(b.get("name"))).append(",");
            sb.append(csv(b.get("netBalance"))).append("\n");
        }

        sb.append("\nSETTLEMENT PLAN\n");
        sb.append("From,To,Amount,Instruction\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> settlementPlan = (List<Map<String, Object>>) data.get("settlementPlan");
        for (Map<String, Object> s : settlementPlan) {
            sb.append(csv(s.get("fromName"))).append(",");
            sb.append(csv(s.get("toName"))).append(",");
            sb.append(csv(s.get("amount"))).append(",");
            sb.append(csv(s.get("text"))).append("\n");
        }

        return sb.toString();
    }

    @Transactional
    public Map<String, Object> clearAllDebtsForGroup(Long groupId) {
        List<Expense> expenses = expenseRepo.findByGroupIdOrderByIdDesc(groupId);

        for (Expense expense : expenses) {
            splitRepo.deleteByExpenseId(expense.getId());
            expenseRepo.deleteById(expense.getId());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "All debts cleared successfully.");
        response.put("groupId", groupId);
        response.put("clearedExpenses", expenses.size());
        response.put("balances", new HashMap<Long, Double>());
        return response;
    }

    // =========================
    // Existing code
    // =========================

    private void mapRequestToEntity(ExpenseRequest req, Expense e) {
        e.setAmount(req.getAmount());
        e.setDescription(req.getDescription());

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
                es.setUserId(uid);
                es.setPercentage(pct);
                es.setAmount(amount);
                splitRepo.save(es);
            }

        } else {
            double splitAmount = totalAmount / participants.size();

            for (Long uid : participants) {
                ExpenseSplit es = new ExpenseSplit();
                es.setExpense(expense);
                es.setUserId(uid);
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

                    User payerUser = e.getPaidBy();
                    Long paidById = payerUser != null ? payerUser.getId() : null;

                    String payerName;
                    if (paidById != null && paidById.equals(userId)) {
                        payerName = "You";
                    } else if (payerUser != null) {
                        if (payerUser.getName() != null && !payerUser.getName().isBlank()) {
                            payerName = payerUser.getName();
                        } else if (payerUser.getEmail() != null && !payerUser.getEmail().isBlank()) {
                            payerName = payerUser.getEmail();
                        } else {
                            payerName = "User " + paidById;
                        }
                    } else {
                        payerName = "Unknown User";
                    }

                    Map<String, Object> activity = new HashMap<>();
                    activity.put("id", e.getId());
                    activity.put("groupId", e.getGroupId());
                    activity.put("desc", e.getDescription());
                    activity.put("amount", e.getAmount());
                    activity.put("category", e.getCategory());
                    activity.put("date", e.getExpenseDate());
                    activity.put("payer", payerName);
                    activity.put("payerId", paidById);
                    return activity;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Long.compare(
                        ((Number) b.get("id")).longValue(),
                        ((Number) a.get("id")).longValue()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // Internal Helpers for Sprint 3
    // =========================

    private List<Map<String, Object>> calculateDirectDebts(Long groupId) {
        List<Expense> expenses = expenseRepo.findByGroupIdOrderByIdDesc(groupId);
        List<Map<String, Object>> directDebts = new ArrayList<>();

        for (Expense expense : expenses) {
            if (expense == null) continue;

            Long payerId = expense.getPaidBy() != null ? expense.getPaidBy().getId() : null;
            if (payerId == null) continue;

            List<ExpenseSplit> splits = splitRepo.findByExpenseId(expense.getId());

            for (ExpenseSplit split : splits) {
                Long participantId = split.getUserId();
                if (participantId == null) continue;

                if (participantId.equals(payerId)) continue;

                double amount = round2(split.getAmount());
                if (amount <= 0.009) continue;

                Map<String, Object> debt = new HashMap<>();
                debt.put("expenseId", expense.getId());
                debt.put("expenseDesc", expense.getDescription());
                debt.put("fromUserId", participantId);
                debt.put("fromName", getUserDisplayName(participantId));
                debt.put("toUserId", payerId);
                debt.put("toName", getUserDisplayName(payerId));
                debt.put("amount", amount);

                directDebts.add(debt);
            }
        }

        return directDebts;
    }

    private Map<Long, Double> calculateGroupBalances(Long groupId) {
        List<Map<String, Object>> directDebts = calculateDirectDebts(groupId);
        Map<Long, Double> balances = new HashMap<>();

        for (Map<String, Object> debt : directDebts) {
            Long fromUserId = ((Number) debt.get("fromUserId")).longValue();
            Long toUserId = ((Number) debt.get("toUserId")).longValue();
            double amount = round2(((Number) debt.get("amount")).doubleValue());

            balances.put(fromUserId, round2(balances.getOrDefault(fromUserId, 0.0) - amount));
            balances.put(toUserId, round2(balances.getOrDefault(toUserId, 0.0) + amount));
        }

        return balances;
    }

    private String getUserDisplayName(Long userId) {
        if (userId == null) return "Unknown User";
        return userRepo.findById(userId)
                .map(user -> {
                    if (user.getName() != null && !user.getName().isBlank()) return user.getName();
                    if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
                    return "User " + userId;
                })
                .orElse("User " + userId);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}