package com.smartworkalarm.presentation.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftPremiumOfferTest {
    @Test
    fun `launch offer communicates automatic scheduling and lifetime pricing`() {
        val offer = ShiftPremiumOffer.launch

        assertEquals(9_900, offer.regularPriceWon)
        assertEquals(5_900, offer.launchPriceWon)
        assertTrue(offer.isLifetime)
        assertTrue(offer.headline.contains("자동으로"))
        assertTrue(offer.headline.contains("켜지고 꺼집니다"))
        assertEquals("5,900원에 평생 이용하기", offer.purchaseLabel)
    }
}
