package dev.ceireader.app.card
import org.junit.Assert.*
import org.junit.Test

class StatusWordTest {
    @Test fun success_is_9000() { assertTrue(StatusWord.isSuccess(0x9000)); assertFalse(StatusWord.isSuccess(0x6982)) }
    @Test fun wrong_pin_63Cx_reports_retries() {
        assertEquals(2, StatusWord.pinRetriesLeft(0x63C2))
        assertEquals(0, StatusWord.pinRetriesLeft(0x63C0))
        assertNull(StatusWord.pinRetriesLeft(0x9000))
        assertNull(StatusWord.pinRetriesLeft(0x6983))
    }
    @Test fun hex_is_uppercase_4_digits() { assertEquals("6982", StatusWord.hex(0x6982)); assertEquals("9000", StatusWord.hex(0x9000)) }
}
