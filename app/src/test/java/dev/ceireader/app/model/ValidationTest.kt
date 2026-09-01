package dev.ceireader.app.model
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun can_must_be_exactly_six_digits() {
        assertTrue(Validation.isValidCan("123456"))
        assertFalse(Validation.isValidCan("12345"))
        assertFalse(Validation.isValidCan("1234567"))
        assertFalse(Validation.isValidCan("12345a"))
        assertFalse(Validation.isValidCan(""))
    }
    @Test fun pin_must_be_exactly_four_digits() {
        assertTrue(Validation.isValidPin("1234"))
        assertFalse(Validation.isValidPin("123"))
        assertFalse(Validation.isValidPin("12345"))
        assertFalse(Validation.isValidPin("abcd"))
        assertFalse(Validation.isValidPin(""))
    }
}
