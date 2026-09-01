package dev.ceireader.app.card
import org.junit.Assert.assertEquals
import org.junit.Test

class ApduTest {
    @Test fun select_by_aid_wraps_00A40400_Lc_aid_and_Le00() {
        assertEquals("00A4040010A000000077030C60000000FE0000050000",
            Apdu.bytesToHex(Apdu.selectByAid("A000000077030C60000000FE00000500")))
    }
    @Test fun select_ef_is_00A4020C02_plus_fid() {
        assertEquals("00A4020C020101", Apdu.bytesToHex(Apdu.selectEf("0101")))
    }
    @Test fun read_binary_default_is_00B0000000() {
        assertEquals("00B0000000", Apdu.bytesToHex(Apdu.readBinary()))
    }
    @Test fun read_binary_encodes_offset_in_p1p2() {
        assertEquals("00B0010000", Apdu.bytesToHex(Apdu.readBinary(offset = 0x0100)))
    }
    @Test fun verify_pin_pads_to_12_with_FF() {
        // "1234" -> 31 32 33 34 then FF*8, header 00 20 00 03 0C
        assertEquals("0020000 3 0C 31323334 FFFFFFFFFFFFFFFF".replace(" ", ""),
            Apdu.bytesToHex(Apdu.verifyPin("1234")))
    }
}
