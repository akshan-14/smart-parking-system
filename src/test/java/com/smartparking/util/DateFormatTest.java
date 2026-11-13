package com.smartparking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DateFormat Asia/Kolkata Timestamp Tests")
class DateFormatTest {

    @Test
    @DisplayName("Should format LocalDateTime in Indian Standard Time style (07 Nov 2026, 3:00 PM)")
    void testDisplayLocalDateTime() {
        LocalDateTime ldt = LocalDateTime.of(2026, 11, 7, 15, 0, 0);
        String formatted = DateFormat.display(ldt);
        assertEquals("07 Nov 2026, 3:00 PM", formatted);
    }

    @Test
    @DisplayName("Should format date-only strings and ISO timestamps")
    void testIsoAndDateParsing() {
        String iso = "2026-11-07T15:00:54";
        String formatted = DateFormat.display(iso);
        assertTrue(formatted.contains("07 Nov 2026"), "Formatted ISO string should contain '07 Nov 2026'");

        assertEquals("—", DateFormat.display((String) null));
        assertEquals("—", DateFormat.display(""));
    }
}
