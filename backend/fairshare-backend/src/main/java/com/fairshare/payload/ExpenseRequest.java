package com.fairshare.payload;

import java.util.List;

/**
 * Represents a request to create or update an expense in a group.
 * Includes all relevant fields like title, amount, payer, participants, category, date, and notes.
 */
public class ExpenseRequest {

    // Title/description of the expense
    private String description;

    // Total amount of the expense
    private double amount;

    // ID of the user who paid for the expense
    private Long paidBy;

    // ID of the group this expense belongs to
    private Long groupId;

    // List of user IDs participating in splitting the expense
    private List<Long> participants;

    // Category of the expense (e.g., Food, Utilities, Rent)
    private String category;

    // Expense date in YYYY-MM-DD format
    private String expenseDate;

    // Optional additional notes
    private String notes;

private String paidByUserName;

    // --- Getters ---
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public Long getPaidBy() { return paidBy; }
    public Long getGroupId() { return groupId; }
    public List<Long> getParticipants() { return participants; }
    public String getCategory() { return category; }
    public String getExpenseDate() { return expenseDate; }
    public String getNotes() { return notes; }

    // --- Setters ---
    public void setDescription(String description) { this.description = description; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setPaidBy(Long paidBy) { this.paidBy = paidBy; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public void setParticipants(List<Long> participants) { this.participants = participants; }
    public void setCategory(String category) { this.category = category; }
    public void setExpenseDate(String expenseDate) { this.expenseDate = expenseDate; }
    public void setNotes(String notes) { this.notes = notes; }
}
