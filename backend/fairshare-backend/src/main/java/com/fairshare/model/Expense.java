package com.fairshare.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="expenses")
public class Expense {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String description;
    
    private Double amount;
    
    /**
     * CHANGED: Instead of a Long ID, we use the User entity.
     * @ManyToOne: Many expenses can be paid by one User.
     * @JoinColumn: Maps to 'paid_by_id' in your database table.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paid_by_id")
    private User paidBy;
    
    // Keeping Group ID as Long for now (or you can map it to a Group entity similarly)
    private Long groupId;

    private String category; 
    
    private LocalDate expenseDate; 
    
    @Column(columnDefinition = "TEXT")
    private String notes; 

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Expense() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    // UPDATED Getter/Setter for User object
    public User getPaidBy() { return paidBy; }
    public void setPaidBy(User paidBy) { this.paidBy = paidBy; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}