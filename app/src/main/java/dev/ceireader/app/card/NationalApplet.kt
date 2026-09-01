package dev.ceireader.app.card

import android.nfc.tech.IsoDep
import java.io.ByteArrayOutputStream
import net.sf.scuba.smartcards.APDUWrapper
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.IsoDepCardService
import net.sf.scuba.smartcards.ResponseAPDU
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.protocol.PACEAPDUSender
import org.jmrtd.protocol.PACEProtocol

/** Thrown by [NationalApplet.login] when VERIFY PIN returns SW=63Cx. */
class WrongPinException(val retriesLeft: Int) : Exception("Wrong PIN, $retriesLeft attempt(s) left")

/** Thrown by [NationalApplet.login] when the PIN is blocked (no retries left). */
class PinBlockedException : Exception("PIN is blocked")

/**
 * Drives the Romanian eID "national applet" flow on the SAME physical card
 * session as [PaceSession] (i.e. after PACE(CAN) + DG2 have already been
 * read from the ICAO applet on the same tag): a plaintext SELECT of the
 * national applet AID, a SECOND PACE(CAN) against that applet to establish
 * a fresh secure-messaging channel, VERIFY PIN, SELECT of the eID DF, then
 * SELECT + chained READ BINARY over the personal-data EFs.
 *
 * Design note (why this doesn't reuse [PaceSession.service]): selecting a
 * different on-card application resets the card's security environment, so
 * the JMRTD [PassportService] secure channel opened by [PaceSession] is
 * dead once we SELECT the national applet. Calling [PassportService.doPACE]
 * a second time on that same instance would reuse its *private* `wrapper`
 * field to (mis-)wrap the plaintext MSE:Set AT that must open the new
 * channel, and [PassportService.sendSelectApplet] always selects the fixed
 * ICAO LDS1 AID, not our national AID. So instead we drive PACE at the
 * JMRTD protocol level ([PACEProtocol] + [PACEAPDUSender]) directly, over a
 * fresh [IsoDepCardService] wrapping the *same already-connected* [isoDep].
 * [IsoDepCardService.open] is a no-op once `IsoDep.isConnected()` is true
 * (verified against the scuba-sc-android 0.0.26 bytecode), so this does not
 * attempt to reconnect the tag.
 *
 * Secure messaging for post-PACE commands (VERIFY PIN, SELECT, READ BINARY)
 * is applied manually here via the [APDUWrapper] returned by PACE, since
 * [PassportService.transmit] (had we used it) does not apply the wrapper —
 * only [PassportService.getInputStream] does, internally.
 */
class NationalApplet(private val isoDep: IsoDep, private val can: String) {

    private val cardService = IsoDepCardService(isoDep)
    private var wrapper: APDUWrapper? = null

    /** SELECTs the national applet, runs PACE #2, and verifies the PIN. */
    fun login(pin: String) {
        cardService.open()

        // Step 1 (spec ss5 step 4): plaintext SELECT of the national applet,
        // sent raw -- NOT through PaceSession's (now-dead) secure channel.
        val selectResp = transmitRaw(Apdu.selectByAid(NATIONAL_AID_HEX))
        check9000(selectResp.sw, "SELECT national applet")

        // Step 2 (spec ss5 step 5): second full PACE(CAN) -> fresh secure channel.
        wrapper = runPace()

        // Step 3 (spec ss5 step 6): VERIFY PIN over the new secure channel.
        val verify = transmitSecure(Apdu.verifyPin(pin))
        mapPinSw(verify.sw)

        // Step 4 (spec ss5 step 7): SELECT the eID DF, secure-messaged.
        val dfSelect = transmitSecure(Apdu.hexToBytes(EID_DF_SELECT_APDU_HEX))
        check9000(dfSelect.sw, "SELECT eID DF")
    }

    /**
     * SELECTs the EF identified by [fidHex] then reads it fully via chained
     * READ BINARY (spec ss5 step 8), secure-messaged. Concatenates all blocks.
     */
    fun readEf(fidHex: String): ByteArray {
        val sel = transmitSecure(Apdu.selectEf(fidHex))
        check9000(sel.sw, "SELECT EF $fidHex")

        val out = ByteArrayOutputStream()
        var offset = 0
        while (true) {
            val resp = transmitSecure(Apdu.readBinary(offset = offset, le = 0))
            // 0x6282 is the one benign non-9000 SW: "EOF reached before Ne
            // bytes", i.e. a legitimate short final READ BINARY block. Any
            // other non-9000 SW -- including other 62xx codes such as 6281
            // "part of returned data may be corrupted" -- is a hard failure:
            // never accept card-flagged-corrupt bytes as valid PII.
            val isEofWarning = resp.sw == 0x6282
            if (!StatusWord.isSuccess(resp.sw) && !isEofWarning) {
                check9000(resp.sw, "READ BINARY EF $fidHex")
            }
            out.write(resp.data)
            offset += resp.data.size
            if (resp.data.size < READ_CHUNK_BYTES || isEofWarning) {
                break
            }
        }
        return out.toByteArray()
    }

    /** Runs PACE(CAN) at the JMRTD protocol level, starting from no prior secure channel. */
    private fun runPace(): APDUWrapper {
        val paceSender = PACEAPDUSender(cardService)
        val protocol = PACEProtocol(
            paceSender,
            /* wrapper = */ null,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            /* shouldCheckMAC = */ false,
        )
        val paceKey = PACEKeySpec.createCANKey(can)
        val result = protocol.doPACE(
            paceKey,
            SecurityInfo.ID_PACE_ECDH_GM_AES_CBC_CMAC_256,
            PACEInfo.toParameterSpec(PACEInfo.PARAM_ID_ECP_BRAINPOOL_P256_R1),
            null,
        )
        return result.wrapper
    }

    private fun transmitRaw(commandBytes: ByteArray): ResponseAPDU =
        cardService.transmit(CommandAPDU(commandBytes))

    /** A secure-messaged command/response pair with the wrapper applied on both sides. */
    private data class SecureResponse(val sw: Int, val data: ByteArray)

    private fun transmitSecure(commandBytes: ByteArray): SecureResponse {
        val w = wrapper ?: throw CardServiceException("Secure channel not established")
        val raw = cardService.transmit(w.wrap(CommandAPDU(commandBytes)))
        // The trailing SW on the wire response is plaintext per ISO 7816-4 SM
        // (DO99 is a MAC-covered copy, not an encryption of it), so it can be
        // read directly off the raw response without unwrapping.
        val sw = raw.sw
        // 0x6282 is the one benign non-9000 SW: "EOF reached before Ne bytes",
        // i.e. a legitimate short final READ BINARY block. Other 62xx codes
        // (e.g. 6281 "part of returned data may be corrupted") are NOT treated
        // as benign -- accepting card-flagged-corrupt bytes as PII is unsafe.
        val data = if (StatusWord.isSuccess(sw) || sw == 0x6282) {
            try {
                w.unwrap(raw).data ?: ByteArray(0)
            } catch (e: Exception) {
                // Do NOT swallow this: a MAC failure / SM desync on an
                // otherwise-successful SW must fail loudly, not silently
                // return empty/partial bytes that readEf's loop could
                // mistake for a legitimate short/EOF block.
                throw CardServiceException("SM unwrap failed for SW=${StatusWord.hex(sw)}", e, sw)
            }
        } else {
            ByteArray(0)
        }
        return SecureResponse(sw, data)
    }

    private fun check9000(sw: Int, what: String) {
        if (!StatusWord.isSuccess(sw)) {
            throw CardServiceException("$what failed: SW=${StatusWord.hex(sw)}", sw)
        }
    }

    private fun mapPinSw(sw: Int) {
        if (StatusWord.isSuccess(sw)) {
            return
        }
        StatusWord.pinRetriesLeft(sw)?.let { retries ->
            if (retries == 0) {
                throw PinBlockedException()
            }
            throw WrongPinException(retries)
        }
        if (sw == 0x6983 || sw == 0x6984) {
            throw PinBlockedException()
        }
        throw CardServiceException("VERIFY PIN failed: SW=${StatusWord.hex(sw)}", sw)
    }

    companion object {
        private const val NATIONAL_AID_HEX = "A000000077030C60000000FE00000500"
        private const val EID_DF_SELECT_APDU_HEX = "00A4040C0FE828BD080FA000000167454441544100"
        private const val READ_CHUNK_BYTES = 256

        /** EF short file IDs for the six personal-data files (spec ss5 step 8). */
        val EF_FIDS = listOf("0101", "0102", "0104", "0106", "0107", "0108")
    }
}
