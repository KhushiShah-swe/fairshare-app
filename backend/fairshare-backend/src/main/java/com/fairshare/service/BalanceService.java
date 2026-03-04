package com.fairshare.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
import com.fairshare.model.User;
import com.fairshare.repository.ExpenseRepository;
import com.fairshare.repository.ExpenseSplitRepository;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class BalanceService {

    @Autowired private ExpenseRepository expenseRepo;
    @Autowired private ExpenseSplitRepository splitRepo;

    public Map<Long, Double> getBalances(Long groupId) {
        Map<Long, Double> balances = new HashMap<>();

        List<Expense> expenses = expenseRepo.findByGroupId(groupId);
        for (Expense e : expenses) {

            // ✅ Supports both designs:
            // - paidBy is Long
            // - paidBy is User
            Long paidById = extractUserId(e.getPaidBy());
            if (paidById != null) {
                balances.put(paidById, balances.getOrDefault(paidById, 0.0) + e.getAmount());
            }

            // Splits for this expense
            List<ExpenseSplit> splits = splitRepo.findByExpense(e);
            for (ExpenseSplit s : splits) {
                Long uid = extractUserId(s.getUserId());
                if (uid != null) {
                    balances.put(uid, balances.getOrDefault(uid, 0.0) - s.getAmount());
                }
            }
        }

        return balances;
    }

    /**
     * Utility to safely extract a userId no matter whether the model uses Long or User.
     */
    private Long extractUserId(Object userOrId) {
        if (userOrId == null) return null;

        if (userOrId instanceof Long) {
            return (Long) userOrId;
        }
        if (userOrId instanceof Integer) {
            return ((Integer) userOrId).longValue();
        }
        if (userOrId instanceof User) {
            return ((User) userOrId).getId();
        }

        // Unknown type
        return null;
    }
}