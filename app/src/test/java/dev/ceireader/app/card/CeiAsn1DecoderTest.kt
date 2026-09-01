package dev.ceireader.app.card

import dev.ceireader.app.model.AddressPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * All bytes below are SYNTHETIC test fixtures built inline — no real personal data.
 * Names/CNP/dates/addresses are fake placeholders chosen only to exercise the parser.
 */
class CeiAsn1DecoderTest {

    // --- Minimal TLV builder for the verified ASN.1 structure (definite-length only). ---

    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), ((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())
    }

    private fun primitive(tag: Int, value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(tag.toByte()) + len(bytes.size) + bytes
    }

    private fun sequence(tag: Int, contents: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + len(contents.size) + contents

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun outerSequence(vararg fields: ByteArray): ByteArray =
        sequence(0x30, concat(*fields))

    // --- Task 7 tests ---

    @Test fun decodePersonal_parses_all_context_tagged_fields() {
        val ef = outerSequence(
            primitive(0x80, "TESTFAM"),
            primitive(0x81, "ANA-MARIA"),
            primitive(0x82, "F"),
            primitive(0x83, "01012000"),
            primitive(0x84, "2000101000000"),
            primitive(0x85, "ROU")
        )
        val result = CeiAsn1Decoder.decodePersonal(ef)
        assertEquals("TESTFAM", result.lastName)
        assertEquals("ANA-MARIA", result.firstName)
        assertEquals("F", result.gender)
        assertEquals("01.01.2000", result.birthDate)
        assertEquals("2000101000000", result.cnp)
        assertEquals("ROU", result.citizenship)
    }

    @Test fun decodeBirth_parses_dateOfBirth_and_placeOfBirth() {
        val ef = outerSequence(
            primitive(0x80, "01012000"),
            primitive(0x81, "Test City")
        )
        val result = CeiAsn1Decoder.decodeBirth(ef)
        assertEquals("01.01.2000", result.dateOfBirth)
        assertEquals("Test City", result.placeOfBirth)
    }

    @Test fun decodeIssuer_parses_all_fields_with_reformatted_dates() {
        val ef = outerSequence(
            primitive(0x80, "AX1234567"),
            primitive(0x81, "15062019"),
            primitive(0x82, "15062029"),
            primitive(0x83, "Test Authority")
        )
        val result = CeiAsn1Decoder.decodeIssuer(ef)
        assertEquals("AX1234567", result.documentSerialNo)
        assertEquals("15.06.2019", result.issuingDate)
        assertEquals("15.06.2029", result.expiryDate)
        assertEquals("Test Authority", result.issuingAuthority)
    }

    @Test fun decodeAddress_returns_current_address_from_tag80() {
        val ef = outerSequence(
            primitive(0x80, "Test Street 1, Test City")
        )
        assertEquals("Test Street 1, Test City", CeiAsn1Decoder.decodeAddress(ef))
    }

    @Test fun decodeAddress_returns_null_when_tag_missing() {
        val ef = outerSequence()
        assertEquals(null, CeiAsn1Decoder.decodeAddress(ef))
    }

    @Test fun decodeAddressPeriods_parses_one_entry_with_empty_address_and_reformatted_dates() {
        val entry = sequence(
            0xA0,
            sequence(
                0x30,
                concat(
                    primitive(0x80, ""),
                    primitive(0x81, "01012020"),
                    primitive(0x82, "31122020")
                )
            )
        )
        val ef = outerSequence(entry)
        val result = CeiAsn1Decoder.decodeAddressPeriods(ef)
        assertEquals(1, result.size)
        assertEquals(AddressPeriod("", "01.01.2020", "31.12.2020"), result[0])
    }

    @Test fun decodeAddressPeriods_parses_multiple_entries() {
        val entry1 = sequence(
            0xA0,
            sequence(
                0x30,
                concat(
                    primitive(0x80, "Test Foreign Address 1"),
                    primitive(0x81, "01012021"),
                    primitive(0x82, "31122021")
                )
            )
        )
        val entry2 = sequence(
            0xA0,
            sequence(
                0x30,
                concat(
                    primitive(0x80, "Test Foreign Address 2"),
                    primitive(0x81, "01012022"),
                    primitive(0x82, "31122022")
                )
            )
        )
        val ef = outerSequence(entry1, entry2)
        val result = CeiAsn1Decoder.decodeAddressPeriods(ef)
        assertEquals(2, result.size)
        assertEquals(AddressPeriod("Test Foreign Address 1", "01.01.2021", "31.12.2021"), result[0])
        assertEquals(AddressPeriod("Test Foreign Address 2", "01.01.2022", "31.12.2022"), result[1])
    }

    @Test fun decodeAddressPeriods_returns_empty_list_when_no_entries() {
        val ef = outerSequence()
        assertEquals(emptyList<AddressPeriod>(), CeiAsn1Decoder.decodeAddressPeriods(ef))
    }

    @Test fun missing_optional_tag_yields_null_field() {
        val ef = outerSequence(
            primitive(0x80, "TESTFAM")
            // firstName, gender, birthDate, cnp, citizenship all missing
        )
        val result = CeiAsn1Decoder.decodePersonal(ef)
        assertEquals("TESTFAM", result.lastName)
        assertEquals(null, result.firstName)
        assertEquals(null, result.gender)
        assertEquals(null, result.birthDate)
        assertEquals(null, result.cnp)
        assertEquals(null, result.citizenship)
    }

    @Test fun non_8digit_value_in_date_field_is_kept_raw() {
        // [0x80] in EF 0102 is dateOfBirth; feed it a non-8-digit value to verify it passes through unchanged.
        val ef = outerSequence(primitive(0x80, "UNKNOWN-DATE"))
        val result = CeiAsn1Decoder.decodeBirth(ef)
        assertEquals("UNKNOWN-DATE", result.dateOfBirth)
        assertEquals(null, result.placeOfBirth)
    }

    @Test fun decodePersonal_trims_trailing_whitespace_padding_from_field_values() {
        // Some EFs pad fixed-width fields with trailing whitespace; the decoder must
        // strip it so callers/UI never see it.
        val ef = outerSequence(
            primitive(0x80, "TESTFAM   "),
            primitive(0x81, "  ANA-MARIA ")
        )
        val result = CeiAsn1Decoder.decodePersonal(ef)
        assertEquals("TESTFAM", result.lastName)
        assertEquals("ANA-MARIA", result.firstName)
    }

    @Test fun decodePersonal_decodes_utf8_diacritics_in_lastName() {
        // Cross-checked against an independent Romanian-eID reader: the card stores octet
        // strings as UTF-8, not ASCII, so Romanian diacritics (S-comma, T-comma, a-breve,
        // i-circumflex, a-circumflex) must round-trip correctly.
        val diacriticLastName = "ȘTEFĂNESCU" // "STEFANESCU" with diacritics
        val nameBytes = diacriticLastName.toByteArray(Charsets.UTF_8)
        val ef = outerSequence(
            byteArrayOf(0x80.toByte()) + len(nameBytes.size) + nameBytes
        )
        val result = CeiAsn1Decoder.decodePersonal(ef)
        assertEquals(diacriticLastName, result.lastName)
    }
}
