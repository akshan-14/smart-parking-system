package com.smartparking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("I18n Multi-Language Localization Tests")
class I18nTest {

    @Test
    @DisplayName("English translations load correctly for primary nav keys")
    void testEnglishTranslation() {
        I18n.setLocale(I18n.EN);
        assertEquals(I18n.EN, I18n.getLocale());

        assertEquals("Overview", I18n.get("nav.overview"));
        assertEquals("Vehicle Entry Gate", I18n.get("nav.entry"));
        assertEquals("Checkout & Bill", I18n.get("nav.exit"));
        assertEquals("Floor Visual Map", I18n.get("nav.floors"));
        assertEquals("Advance Booking", I18n.get("nav.reservations"));
        assertEquals("Monthly Passes", I18n.get("nav.membership"));
        assertEquals("Find Vehicle", I18n.get("nav.search"));
        assertEquals("Audit & Reports", I18n.get("nav.reports"));
    }

    @Test
    @DisplayName("Hindi translations load correctly for primary nav keys")
    void testHindiTranslation() {
        I18n.setLocale(I18n.HI);
        assertEquals(I18n.HI, I18n.getLocale());

        assertEquals("अवलोकन", I18n.get("nav.overview"));
        assertEquals("वाहन प्रवेश द्वार", I18n.get("nav.entry"));
        assertEquals("चेकआउट और बिल", I18n.get("nav.exit"));
        assertEquals("फ़्लोर विज़ुअल मैप", I18n.get("nav.floors"));
        assertEquals("अग्रिम बुकिंग", I18n.get("nav.reservations"));
        assertEquals("मासिक पास", I18n.get("nav.membership"));
        assertEquals("वाहन खोजें", I18n.get("nav.search"));
        assertEquals("ऑडिट और रिपोर्ट", I18n.get("nav.reports"));

        // Switch back to English for other tests
        I18n.setLocale(I18n.EN);
    }

    @Test
    @DisplayName("Missing key returns key name gracefully without throwing")
    void testMissingKeyFallback() {
        String missing = I18n.get("non.existent.key.for.test");
        assertEquals("non.existent.key.for.test", missing);
    }
}
