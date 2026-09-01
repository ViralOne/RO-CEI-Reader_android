package dev.ceireader.app.model

object Validation {
    fun isValidCan(s: String): Boolean = s.length == 6 && s.all { it.isDigit() }
    fun isValidPin(s: String): Boolean = s.length in 4..12 && s.all { it.isDigit() }
}
