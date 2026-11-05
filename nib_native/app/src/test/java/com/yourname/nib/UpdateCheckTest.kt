package com.yourname.nib

import com.yourname.nib.core.update.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun patchBumpIsNewer() {
        assertTrue(isNewerVersion("0.1.13", "0.1.12"))
        assertFalse(isNewerVersion("0.1.12", "0.1.12"))
        assertFalse(isNewerVersion("0.1.11", "0.1.12"))
    }

    @Test fun minorBeatsPatch() {
        assertTrue(isNewerVersion("0.2.0", "0.1.99"))
        assertFalse(isNewerVersion("0.1.99", "0.2.0"))
    }

    @Test fun ignoresLeadingV() {
        assertTrue(isNewerVersion("v0.2.0", "0.1.12"))
        assertTrue(isNewerVersion("0.2.0", "v0.1.12"))
    }

    @Test fun missingPartsCountAsZero() {
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
        assertTrue(isNewerVersion("2", "1.9.9"))
    }

    @Test fun nonNumericPartsFallBackToZero() {
        assertFalse(isNewerVersion("abc", "0.1.12"))
        assertTrue(isNewerVersion("0.2.0-beta", "0.1.12"))
    }
}
