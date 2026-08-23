package com.smartworkalarm.sound

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendedSoundCatalogTest {
    @Test fun `catalog has six stable unique app resource sounds`() {
        assertEquals(6, RecommendedSoundCatalog.all.size)
        assertEquals(6, RecommendedSoundCatalog.all.map { it.id }.distinct().size)
        assertEquals(
            "android.resource://com.smartworkalarm/raw/sunrise_pop",
            RecommendedSoundCatalog.all.first().uri,
        )
    }
}
