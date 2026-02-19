package com.fairshare.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
import com.fairshare.payload.ExpenseRequest;
import com.fairshare.repository.ExpenseRepository;
import com.fairshare.repository.ExpenseSplitRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepo;
    @Autowired private ExpenseSplitRepository splitRepo;

    /**
     * FETCH SINGLE: Pre-fills the Edit form.
     */
    public Expense getExpenseById(Long id) {
        return expenseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
    }

    /**
     * FETCH GROUP HISTORY: Returns all expenses for a specific group.
     */
    public List<Expense> getExpensesByGroupId(Long groupId) {
        return expenseRepo.findByGroupIdOrderByIdDesc(groupId);
    }

    /**
     * ADD: Creates an expense with all new metadata (Category, Date, Notes).
     */
    @Transactional
    public void addExpense(ExpenseRequest req) {
        Expense e = new Expense();
        mapRequestToEntity(req, e);
        e.setGroupId(req.getGroupId()); // Group ID usually doesn't change on edit, but set here on add
        
        Expense savedExpense = expenseRepo.save(e);
        saveSplits(savedExpense, req.getParticipants(), req.getAmount());
    }

    /**
     * EDIT: Updates the expense metadata and recalculates splits.
     */
    @Transactional
    public void editExpense(Long id, ExpenseRequest req) {
        Expense e = expenseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        // Update fields
        mapRequestToEntity(req, e);
        expenseRepo.save(e);

        // Clear and recreate splits to reflect new amount or participants
        splitRepo.deleteByExpenseId(id);
        saveSplits(e, req.getParticipants(), req.getAmount());
    }

    /**
     * PRIVATE HELPER: Maps the payload to the Entity to avoid code duplication.
     */
    private void mapRequestToEntity(ExpenseRequest req, Expense e) {
        e.setAmount(req.getAmount());
        e.setDescription(req.getDescription());
        e.setPaidBy(req.getPaidBy());
        e.setCategory(req.getCategory());
        e.setNotes(req.getNotes());
        
        // Handle Date parsing from String (YYYY-MM-DD)
        if (req.getExpenseDate() != null && !req.getExpenseDate().isEmpty()) {
            e.setExpenseDate(LocalDate.parse(req.getExpenseDate()));
        } else {
            e.setExpenseDate(LocalDate.now()); // Fallback to today
        }
    }

    @Transactional
    public void deleteExpense(Long id) {
        splitRepo.deleteByExpenseId(id);
        expenseRepo.deleteById(id);
    }

    private void saveSplits(Expense expense, List<Long> participants, double totalAmount) {
        if (participants == null || participants.isEmpty()) {
            throw new RuntimeException("Expense must have at least one participant.");
        }

        double splitAmount = totalAmount / participants.size();
        
        for (Long uid : participants) {
            ExpenseSplit es = new ExpenseSplit();
            es.setExpense(expense);
            es.setUserId(uid);
            es.setAmount(splitAmount);
            splitRepo.save(es);
        }
    }

    /**
     * BALANCE LOGIC: Calculates net debt per group.
     */
    @Transactional(readOnly = true)
    public Map<Long, Double> getUserBalances(Long userId) {
        List<ExpenseSplit> userSplits = splitRepo.findByUserId(userId);
        Map<Long, Double> groupBalances = new HashMap<>();

        for (ExpenseSplit split : userSplits) {
            Expense expense = split.getExpense();
            if (expense == null) continue;

            double effect = expense.getPaidBy().equals(userId) 
                            ? (expense.getAmount() - split.getAmount()) 
                            : -split.getAmount();

            Long gId = expense.getGroupId();
            groupBalances.put(gId, groupBalances.getOrDefault(gId, 0.0) + effect);
        }
        return groupBalances;
    }

    /**
     * RECENT ACTIVITY: Maps splits to a readable activity list for the UI.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentActivity(Long userId) {
        return splitRepo.findByUserId(userId).stream()
            .map(split -> {
                Expense e = split.getExpense();
                if (e == null) return null;
                
                Map<String, Object> activity = new HashMap<>();
                activity.put("id", e.getId());
                activity.put("desc", e.getDescription());
                activity.put("amount", e.getAmount());
                activity.put("category", e.getCategory());
                activity.put("date", e.getExpenseDate());
                activity.put("payer", e.getPaidBy().equals(userId) ? "You" : "User " + e.getPaidBy());
                return activity;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}