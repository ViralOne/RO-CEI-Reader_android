package dev.ceireader.app.card

/** Helpers for interpreting a raw ISO 7816-4 status word (SW1SW2). */
object StatusWord {
    fun isSuccess(sw: Int): Boolean = sw == 0x9000
    fun pinRetriesLeft(sw: Int): Int? = if (sw and 0xFFF0 == 0x63C0) sw and 0x000F else null
    fun hex(sw: Int): String = String.format("%04X", sw and 0xFFFF)
}
