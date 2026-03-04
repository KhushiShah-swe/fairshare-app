package com.fairshare.model;

import jakarta.persistence.*;

@Entity
@Table(name = "expense_splits")
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    // ID of the user this split belongs to
    @Column(name = "user_id")
    private Long userId;

    // Final calculated amount this user owes for this expense
    private Double amount;

    // Sprint 2: Percentage used for PERCENTAGE split type (null for EQUAL)
    private Double percentage;

    // -------- Getters --------
    public Long getId() { return id; }
    public Expense getExpense() { return expense; }
    public Long getUserId() { return userId; }
    public Double getAmount() { return amount; }
    public Double getPercentage() { return percentage; }

    // -------- Setters --------
    public void setId(Long id) { this.id = id; }
    public void setExpense(Expense expense) { this.expense = expense; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setAmount(Double amount) { this.amount = amount; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }
}