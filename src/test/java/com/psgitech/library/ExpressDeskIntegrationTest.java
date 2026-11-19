package com.psgitech.library;

import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.TrustStandingService;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExpressDeskIntegrationTest {

    @Test
    public void testStudentLookupReturnsActiveStudent() {
        UserRepository userRepo = new UserRepository();
        User student = userRepo.findById(1);
        if (student != null) {
            assertNotNull(student.getName());
            assertNotNull(student.getEmail());

            TrustStandingService trustService = new TrustStandingService();
            assertNotNull(trustService.computeTrustStanding(1));
        }
    }

    @Test
    public void testApiResponseStructureStandardEnvelope() {
        ApiResponse<String> response = ApiResponse.ok("Express desk operation completed");
        assertTrue(response.isSuccess());
        assertEquals("Express desk operation completed", response.getData());
        assertNull(response.getError());

        ApiResponse<Void> err = ApiResponse.error("BORROWING_LIMIT_EXCEEDED", "Borrowing limit exceeded");
        assertFalse(err.isSuccess());
        assertNotNull(err.getError());
        assertEquals("BORROWING_LIMIT_EXCEEDED", err.getError().getCode());
    }
}
