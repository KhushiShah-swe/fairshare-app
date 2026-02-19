package com.fairshare.dto;

import java.util.List;

public class ExpenseRequest {
    private String description;
    private Double amount;
    private Long groupId;
    private Long paidBy;
    private List<Long> participants;

    // getters and setters
}
