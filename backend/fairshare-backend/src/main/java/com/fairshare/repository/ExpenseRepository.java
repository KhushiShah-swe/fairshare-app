package com.fairshare.repository;

import com.fairshare.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Finds all expenses associated with a specific group.
     */
    List<Expense> findByGroupId(Long groupId);

    /**
     * Finds all expenses for a group, sorted by ID descending (newest first).
     * This is the preferred method for displaying the group's expense history.
     */
    List<Expense> findByGroupIdOrderByIdDesc(Long groupId);

    /**
     * Finds all expenses paid for by a specific user.
     * Useful for showing a user how much they have personally spent across all groups.
     */
List<Expense> findByPaidBy_Id(Long paidById);
    /**
     * CRITICAL: Used when a group is deleted.
     * Removes all expenses belonging to the group to maintain database integrity.
     */
    @Modifying
    @Transactional
    void deleteByGroupId(Long groupId);
}