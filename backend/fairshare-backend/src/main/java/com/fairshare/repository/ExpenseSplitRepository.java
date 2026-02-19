package com.fairshare.repository;

import com.fairshare.model.ExpenseSplit;
import com.fairshare.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
    
    /**
     * Finds splits by the full Expense object.
     */
    List<ExpenseSplit> findByExpense(Expense expense);

    /**
     * CRITICAL: Used by ExpenseService.getUserBalances and getRecentActivity.
     * This is the bridge that connects the User to all their transactions.
     */
    List<ExpenseSplit> findByUserId(Long userId);

    /**
     * Used for cleanup or specific expense calculations.
     */
    List<ExpenseSplit> findByExpenseId(Long expenseId);

    /**
     * REQUIRED FOR DELETE/EDIT LOGIC:
     * Wipes all splits associated with a parent expense.
     * @Modifying: Informs Spring that this is a DML operation (INSERT, UPDATE, DELETE).
     * @Transactional: Ensures the operation is executed within a transaction context.
     */
    @Modifying
    @Transactional
    void deleteByExpenseId(Long expenseId);
}