package dev.ceireader.app.card

import dev.ceireader.app.model.AddressPeriod

data class PersonalFields(
    val lastName: String?,
    val firstName: String?,
    val gender: String?,
    val birthDate: String?,
    val cnp: String?,
    val citizenship: String?
)

data class BirthFields(
    val dateOfBirth: String?,
    val placeOfBirth: String?
)

data class IssuerFields(
    val documentSerialNo: String?,
    val issuingDate: String?,
    val expiryDate: String?,
    val issuingAuthority: String?
)

/**
 * Decodes the raw bytes of Romanian eID EFs (0101, 0102, 0104, 0106, 0107, 0108) into typed
 * fields. Each EF is a definite-length BER/DER SEQUENCE (tag 0x30) whose contents are
 * context-tagged primitive fields (tags 0x80, 0x81, ...) holding ASCII strings; EF 0107/0108
 * additionally nest [0xA0]-wrapped SEQUENCE(0x30) entries for each address period.
 *
 * Implemented as a small hand-rolled definite-length TLV walker rather than pulling in
 * BouncyCastle's ASN.1 machinery, since the structure here is a fixed, shallow, all-primitive
 * shape that doesn't need a general-purpose parser.
 */
object CeiAsn1Decoder {

    private class Tlv(val tag: Int, val value: ByteArray)

    /** Reads one definite-length TLV item starting at [offset]. Returns null past the input's end. */
    private fun readTlv(data: ByteArray, offset: Int): Pair<Tlv, Int>? {
        if (offset >= data.size) return null
        var pos = offset
        val tag = data[pos].toInt() and 0xFF
        pos += 1
        if (pos >= data.size) return null
        val first = data[pos].toInt() and 0xFF
        pos += 1
        val length: Int
        if (first < 0x80) {
            length = first
        } else {
            val numBytes = first and 0x7F
            if (numBytes == 0 || pos + numBytes > data.size) return null
            var l = 0
            repeat(numBytes) {
                l = (l shl 8) or (data[pos].toInt() and 0xFF)
                pos += 1
            }
            length = l
        }
        if (pos + length > data.size) return null
        val value = data.copyOfRange(pos, pos + length)
        return Tlv(tag, value) to (pos + length)
    }

    /** Parses the top-level SEQUENCE's contents into a tag -> raw value map (first occurrence wins). */
    private fun parseTopLevelFields(ef: ByteArray): Map<Int, ByteArray> {
        val outer = readTlv(ef, 0) ?: return emptyMap()
        val contents = outer.first.value
        val fields = mutableMapOf<Int, ByteArray>()
        var pos = 0
        while (pos < contents.size) {
            val (tlv, next) = readTlv(contents, pos) ?: break
            if (tlv.tag !in fields) fields[tlv.tag] = tlv.value
            pos = next
        }
        return fields
    }

    private fun ascii(bytes: ByteArray?): String? =
        bytes?.toString(Charsets.UTF_8)

    private val EIGHT_DIGITS = Regex("^\\d{8}$")

    /** Reformats an 8-char DDMMYYYY ASCII string to Romanian DD.MM.YYYY; returns other values unchanged. */
    private fun reformatDate(raw: String?): String? {
        if (raw == null || !EIGHT_DIGITS.matches(raw)) return raw
        val dd = raw.substring(0, 2)
        val mm = raw.substring(2, 4)
        val yyyy = raw.substring(4, 8)
        return "$dd.$mm.$yyyy"
    }

    fun decodePersonal(ef: ByteArray): PersonalFields {
        val fields = parseTopLevelFields(ef)
        return PersonalFields(
            lastName = ascii(fields[0x80]),
            firstName = ascii(fields[0x81]),
            gender = ascii(fields[0x82]),
            birthDate = reformatDate(ascii(fields[0x83])),
            cnp = ascii(fields[0x84]),
            citizenship = ascii(fields[0x85])
        )
    }

    fun decodeBirth(ef: ByteArray): BirthFields {
        val fields = parseTopLevelFields(ef)
        return BirthFields(
            dateOfBirth = reformatDate(ascii(fields[0x80])),
            placeOfBirth = ascii(fields[0x81])
        )
    }

    fun decodeIssuer(ef: ByteArray): IssuerFields {
        val fields = parseTopLevelFields(ef)
        return IssuerFields(
            documentSerialNo = ascii(fields[0x80]),
            issuingDate = reformatDate(ascii(fields[0x81])),
            expiryDate = reformatDate(ascii(fields[0x82])),
            issuingAuthority = ascii(fields[0x83])
        )
    }

    fun decodeAddress(ef: ByteArray): String? {
        val fields = parseTopLevelFields(ef)
        return ascii(fields[0x80])
    }

    fun decodeAddressPeriods(ef: ByteArray): List<AddressPeriod> {
        val outer = readTlv(ef, 0) ?: return emptyList()
        val contents = outer.first.value
        val periods = mutableListOf<AddressPeriod>()
        var pos = 0
        while (pos < contents.size) {
            val (entryTlv, next) = readTlv(contents, pos) ?: break
            pos = next
            if (entryTlv.tag != 0xA0) continue
            // Each [0xA0] wraps a single SEQUENCE(0x30) with [0x80]=address, [0x81]=startDate, [0x82]=endDate.
            val inner = readTlv(entryTlv.value, 0) ?: continue
            val entryFields = mutableMapOf<Int, ByteArray>()
            var innerPos = 0
            while (innerPos < inner.first.value.size) {
                val (tlv, innerNext) = readTlv(inner.first.value, innerPos) ?: break
                if (tlv.tag !in entryFields) entryFields[tlv.tag] = tlv.value
                innerPos = innerNext
            }
            periods.add(
                AddressPeriod(
                    address = ascii(entryFields[0x80]) ?: "",
                    startDate = reformatDate(ascii(entryFields[0x81])),
                    endDate = reformatDate(ascii(entryFields[0x82]))
                )
            )
        }
        return periods
    }
}
