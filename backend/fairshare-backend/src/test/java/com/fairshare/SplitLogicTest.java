package com.fairshare;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitLogicTest {

    double calculateSplit(double total, int people) {
        if (people <= 0) return 0;
        return total / people;
    }

    @Test
    void testEqualSplit() {
        assertEquals(100.00, calculateSplit(300.00, 3));
    }

    @Test
    void testPercentageSplit_FAIL() {
        // INTENTIONAL FAIL for the rubric
        // Expected $210 (70%), but logic returns $100
        assertEquals(210.00, calculateSplit(300.00, 3), "Percentage split logic not implemented.");
    }
}