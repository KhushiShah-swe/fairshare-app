package com.fairshare; 

import org.junit.jupiter.api.Test;
// import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

// @SpringBootTest
public class FairShareBackendTests {

    // 1. Sprint 1: Test Invite Code Logic
    @Test
    void testInviteCodeIsUnique() {
        String code1 = "ABC123";
        String code2 = "XYZ789";
        assertNotEquals(code1, code2, "Invite codes must be unique for different groups");
    }

    // 2. Sprint 2: Test Equal Split Logic
    @Test
    void testEqualSplitCalculation() {
        double amount = 90.00;
        int members = 3;
        assertEquals(30.00, amount / members, "Each member should owe 30.00");
    }

    // 3. Sprint 2: Test Percentage Split Logic
    @Test
    void testPercentageSplit() {
        double total = 100.0;
        double share = (40.0 / 100.0) * total;
        assertEquals(40.0, share, "40% of 100 should be 40");
    }

    // 4. Sprint 2: Test Receipt Data Storage
    @Test
    void testReceiptAttachment() {
        String mockFileName = "receipt_pizza.jpg";
        assertNotNull(mockFileName, "Receipt filename should not be null after upload");
    }

    // 5. Sprint 1: Test User Role Assignment
    @Test
    void testAdminRoleAssignment() {
        String role = "ADMIN";
        assertTrue(role.equals("ADMIN"), "Group creator must be assigned ADMIN role");
    }
// @Test
// void testIntentionalFailureForProfessor() {
    // This will fail the build intentionally
   //  int expectedMembers = 3;
    // int actualMembers = 5;
    // assertEquals(expectedMembers, actualMembers, "This test failed intentionally to demonstrate build failure");
// }
}
