package com.faforever.mobile.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStatePrefsTest {
    @Test fun parsesSavedScroll() = assertEquals(12 to 34, UiStatePrefs.parseScroll("12,34"))

    @Test fun missingScrollIsTop() = assertEquals(0 to 0, UiStatePrefs.parseScroll(null))

    @Test fun malformedOrNegativeScrollFallsBackToTop() {
        assertEquals(0 to 0, UiStatePrefs.parseScroll("x,y"))
        assertEquals(0 to 0, UiStatePrefs.parseScroll("-3,-9"))
        assertEquals(5 to 0, UiStatePrefs.parseScroll("5"))
    }
}
