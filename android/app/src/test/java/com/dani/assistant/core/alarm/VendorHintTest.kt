package com.dani.assistant.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorHintTest {
    @Test fun knownVendors() {
        assertTrue(AlarmReliability.vendorHint("Xiaomi").startsWith("Xiaomi"))
        assertTrue(AlarmReliability.vendorHint("POCO").startsWith("Xiaomi"))
        assertTrue(AlarmReliability.vendorHint("HUAWEI").startsWith("Huawei"))
        assertTrue(AlarmReliability.vendorHint("realme").startsWith("Oppo"))
        assertTrue(AlarmReliability.vendorHint("samsung").startsWith("Samsung"))
    }
    @Test fun unknownVendorHasNoHint() {
        assertEquals("", AlarmReliability.vendorHint("Google"))
        assertEquals("", AlarmReliability.vendorHint(""))
    }
    @Test fun allGoodRequiresEverything() {
        assertTrue(ReliabilityStatus(true, true, true).allGood)
        assertTrue(!ReliabilityStatus(true, true, false).allGood)
        assertTrue(!ReliabilityStatus(false, true, true).allGood)
    }
}
