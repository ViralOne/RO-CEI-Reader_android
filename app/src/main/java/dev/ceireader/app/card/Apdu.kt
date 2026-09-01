package dev.ceireader.app.card

/** Builds the raw ISO 7816-4 command APDUs used to drive the national eID applet. */
object Apdu {
    fun hexToBytes(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun bytesToHex(b: ByteArray): String =
        b.joinToString("") { String.format("%02X", it.toInt() and 0xFF) }

    fun selectByAid(aidHex: String): ByteArray {
        val aid = hexToBytes(aidHex)
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }
    fun selectEf(fidHex: String): ByteArray {
        val fid = hexToBytes(fidHex)
        return byteArrayOf(0x00, 0xA4.toByte(), 0x02, 0x0C, fid.size.toByte()) + fid
    }
    fun readBinary(offset: Int = 0, le: Int = 0): ByteArray =
        byteArrayOf(0x00, 0xB0.toByte(), ((offset shr 8) and 0xFF).toByte(), (offset and 0xFF).toByte(), (le and 0xFF).toByte())
    fun verifyPin(pin: String, p2: Int = 0x03): ByteArray {
        val body = ByteArray(12) { 0xFF.toByte() }
        val bytes = pin.toByteArray(Charsets.UTF_8)
        System.arraycopy(bytes, 0, body, 0, bytes.size)
        return byteArrayOf(0x00, 0x20, 0x00, p2.toByte(), 0x0C) + body
    }
}
