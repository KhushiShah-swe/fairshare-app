package com.fairshare.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fairshare.model.Expense;
import com.fairshare.model.ExpenseSplit;
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
            // Make sure getters exist in Expense.java
            balances.put(e.getPaidBy(), balances.getOrDefault(e.getPaidBy(), 0.0) + e.getAmount());

            List<ExpenseSplit> splits = splitRepo.findByExpense(e);
            for (ExpenseSplit s : splits) {
                // Make sure getters exist in ExpenseSplit.java
                balances.put(s.getUserId(), balances.getOrDefault(s.getUserId(), 0.0) - s.getAmount());
            }
        }

        return balances;
    }
}
