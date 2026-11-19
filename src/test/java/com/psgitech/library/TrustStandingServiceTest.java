package com.psgitech.library;

import com.psgitech.library.dto.librarian.TrustStandingDTO;
import com.psgitech.library.service.TrustStandingService;
import org.junit.Test;

import static org.junit.Assert.*;

public class TrustStandingServiceTest {

    @Test
    public void testComputeTrustStandingReturnsNonNullDTO() {
        TrustStandingService service = new TrustStandingService();
        TrustStandingDTO dto = service.computeTrustStanding(1);

        assertNotNull(dto);
        assertEquals(1, dto.getStudentId());
        assertNotNull(dto.getTier());
        assertNotNull(dto.getExplanation());
        assertTrue(dto.getOnTimeRatePercent() >= 0.0 && dto.getOnTimeRatePercent() <= 100.0);
    }

    @Test
    public void testNonExistentStudentReceivesStandardFallback() {
        TrustStandingService service = new TrustStandingService();
        TrustStandingDTO dto = service.computeTrustStanding(999999);

        assertNotNull(dto);
        assertEquals(TrustStandingDTO.Tier.STANDARD, dto.getTier());
    }

    @Test
    public void testGoodwillEligibilityRule() {
        TrustStandingDTO dto = new TrustStandingDTO();
        dto.setTier(TrustStandingDTO.Tier.EXEMPLARY);
        dto.setGoodwillAlreadyUsed(false);
        dto.setEligibleForGoodwill(dto.getTier() == TrustStandingDTO.Tier.EXEMPLARY && !dto.isGoodwillAlreadyUsed());

        assertTrue(dto.isEligibleForGoodwill());

        // Once used, eligibility drops
        dto.setGoodwillAlreadyUsed(true);
        dto.setEligibleForGoodwill(dto.getTier() == TrustStandingDTO.Tier.EXEMPLARY && !dto.isGoodwillAlreadyUsed());
        assertFalse(dto.isEligibleForGoodwill());
    }
}
