package com.fairshare.payload;

import java.util.List;
import java.util.Map;

/**
 * Represents a request to create or update an expense in a group.
 * Includes all relevant fields like title, amount, payer, participants, category, date, and notes.
 *
 * Sprint 2 additions:
 * - splitType: EQUAL or PERCENTAGE
 * - percentages: per-user percentage map for percentage split
 *
 * Notes:
 * - "Split among selected members" is achieved by sending only the selected userIds in participants.
 * - For PERCENTAGE splitType, percentages should include entries for the selected participants,
 *   and the sum should be 100 (validated in service/controller).
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
    // (Sprint 2: can be a subset of group members)
    private List<Long> participants;

    // Category of the expense (e.g., Food, Utilities, Rent)
    private String category;

    // Expense date in YYYY-MM-DD format
    private String expenseDate;

    // Optional additional notes
    private String notes;

    // Optional payer name (if your frontend sends it / or you use it for display)
    private String paidByUserName;

    /**
     * Split type for this expense.
     * Expected values (recommended):
     * - "EQUAL"
     * - "PERCENTAGE"
     *
     * If null/blank, backend can default to "EQUAL" for backward compatibility.
     */
    private String splitType;

    /**
     * Percentage map for PERCENTAGE splits.
     * Key: userId (as String, because JSON object keys are strings)
     * Value: percentage (e.g., 50.0)
     *
     * Example JSON:
     * "percentages": { "12": 60.0, "15": 40.0 }
     */
    private Map<String, Double> percentages;

    // --- Getters ---
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public Long getPaidBy() { return paidBy; }
    public Long getGroupId() { return groupId; }
    public List<Long> getParticipants() { return participants; }
    public String getCategory() { return category; }
    public String getExpenseDate() { return expenseDate; }
    public String getNotes() { return notes; }
    public String getPaidByUserName() { return paidByUserName; }

    public String getSplitType() { return splitType; }
    public Map<String, Double> getPercentages() { return percentages; }

    // --- Setters ---
    public void setDescription(String description) { this.description = description; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setPaidBy(Long paidBy) { this.paidBy = paidBy; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public void setParticipants(List<Long> participants) { this.participants = participants; }
    public void setCategory(String category) { this.category = category; }
    public void setExpenseDate(String expenseDate) { this.expenseDate = expenseDate; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setPaidByUserName(String paidByUserName) { this.paidByUserName = paidByUserName; }

    public void setSplitType(String splitType) { this.splitType = splitType; }
    public void setPercentages(Map<String, Double> percentages) { this.percentages = percentages; }
}