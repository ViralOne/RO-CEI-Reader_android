package dev.ceireader.app.card

import android.nfc.TagLostException
import dev.ceireader.app.model.ReadErrorKind
import java.io.IOException
import net.sf.scuba.smartcards.CardServiceException
import org.junit.Assert.assertEquals
import org.junit.Test

class CeiCardReaderErrorMappingTest {
    private val r = CeiCardReader()

    @Test
    fun wrong_pin_maps_with_retries() {
        val e = r.mapError(WrongPinException(2))
        assertEquals(ReadErrorKind.WRONG_PIN, e.kind)
        assertEquals(2, e.retriesLeft)
    }

    @Test
    fun pin_blocked_maps() {
        assertEquals(ReadErrorKind.PIN_BLOCKED, r.mapError(PinBlockedException()).kind)
    }

    @Test
    fun io_maps_to_card_lost() {
        assertEquals(ReadErrorKind.CARD_LOST, r.mapError(IOException()).kind)
    }

    @Test
    fun tag_lost_maps_to_card_lost() {
        assertEquals(ReadErrorKind.CARD_LOST, r.mapError(TagLostException()).kind)
    }

    @Test
    fun generic_exception_maps_to_unknown() {
        assertEquals(ReadErrorKind.UNKNOWN, r.mapError(RuntimeException("boom")).kind)
    }

    @Test
    fun card_service_exception_maps_to_communication() {
        assertEquals(
            ReadErrorKind.COMMUNICATION,
            r.mapError(CardServiceException("PACE failed")).kind,
        )
    }
}
