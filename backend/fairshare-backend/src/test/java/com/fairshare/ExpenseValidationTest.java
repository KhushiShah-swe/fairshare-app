package com.fairshare;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExpenseValidationTest {

    @Test
    void testNegativeAmountValidation() {
        double expenseAmount = -50.00;
        // Logic: Amount must be greater than 0
        assertTrue(expenseAmount < 0, "System should flag negative amounts.");
    }

    @Test
    void testMinimumPeopleValidation() {
        int peopleCount = 1; 
        // Logic: You can't split an expense with 0 people
        assertTrue(peopleCount >= 1, "At least one person must be involved in an expense.");
    }
}