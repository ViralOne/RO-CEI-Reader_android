package dev.ceireader.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the [ReadViewModel.credentialsReady] gate that [ReadViewModel.onTag]
 * consults before starting a read. Without it, tapping a card with an empty or
 * half-typed CAN/PIN still ran the full PACE handshake, which the card rejects
 * with SW 0x6300 -- surfacing to the user as "Eroare de comunicație" rather
 * than anything pointing at the missing credentials.
 */
class ReadViewModelCredentialsTest {

    private fun vmWith(can: String, pin: String) = ReadViewModel().apply {
        this.can = can
        this.pin = pin
    }

    @Test
    fun ready_when_can_is_6_digits_and_pin_is_4() {
        assertTrue(vmWith("123456", "1234").credentialsReady)
    }

    @Test
    fun not_ready_when_both_blank() {
        assertFalse(vmWith("", "").credentialsReady)
    }

    @Test
    fun not_ready_when_can_blank() {
        assertFalse(vmWith("", "1234").credentialsReady)
    }

    @Test
    fun not_ready_when_pin_blank() {
        assertFalse(vmWith("123456", "").credentialsReady)
    }

    @Test
    fun not_ready_when_can_too_short() {
        assertFalse(vmWith("12345", "1234").credentialsReady)
    }

    @Test
    fun not_ready_when_pin_too_short() {
        assertFalse(vmWith("123456", "123").credentialsReady)
    }
}
