package dev.ceireader.app.card

import android.nfc.tech.IsoDep
import android.util.Log
import dev.ceireader.app.model.CeiData
import dev.ceireader.app.model.ReadErrorKind
import dev.ceireader.app.model.ReadState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.sf.scuba.smartcards.CardServiceException

/**
 * Orchestrates the full Romanian eID card read on an already-connected
 * [IsoDep] tag: PACE(CAN) + DG2 photo over the ICAO applet, then the
 * national applet SELECT + PACE#2 + PIN + the six personal-data EFs, decoded into a single
 * [CeiData]. Mirrors the sequence hardware-verified in the MainActivity
 * debug harness (Tasks 5/6).
 */
class CeiCardReader {

    /**
     * Cold [Flow] emitting [ReadState.Started] -> [ReadState.ReadingCard] ->
     * [ReadState.Finished] on success, or [ReadState.Error] (via [mapError])
     * on failure. Intended to run on [Dispatchers.IO]; card resources opened
     * during the read are always closed before the flow completes.
     *
     * [includePhoto] (default `false`) picks one of two flows:
     * - `false` (fast path): skips [PaceSession] entirely -- no ICAO PACE,
     *   no DG2 read -- and goes straight to the national applet (SELECT +
     *   PACE#2 + PIN + the six text EFs), producing [CeiData] with
     *   `faceImage = null`. This removes one full PACE handshake and the
     *   DG2 round trips.
     * - `true` (full path): the original flow (PACE#1 -> DG2 -> national
     *   applet -> PACE#2 -> PIN -> EFs), using extended-length APDUs for the
     *   DG2 read when the tag supports them (see [PaceSession.open]).
     */
    fun read(isoDep: IsoDep, can: String, pin: String, includePhoto: Boolean = false): Flow<ReadState> = flow {
        emit(ReadState.Started)
        var paceSession: PaceSession? = null
        try {
            var photo: ByteArray? = null

            if (includePhoto) {
                val extendedSupported = isoDep.isExtendedLengthApduSupported()
                var session = PaceSession(isoDep)
                paceSession = session

                photo = try {
                    val passportService = session.open(can, extendedSupported)
                    val bytes = session.readFacePhoto(passportService)
                    Log.d(TAG, "DG2 read succeeded (extended=$extendedSupported)")
                    bytes
                } catch (e: Exception) {
                    // Safe fallback (finding 1): the tag reported
                    // extended-length support but the FIRST extended-length
                    // open+DG2 attempt still failed -- either as a
                    // CardServiceException (JMRTD-level failure) or as an
                    // IOException/TagLostException (the extended-length
                    // transceive itself was rejected by the NFC
                    // stack/hardware, which can surface from either open() or
                    // the DG2 read). Either way, retry the whole open+DG2
                    // read at normal length rather than surfacing a spurious
                    // failure. Only re-throw if this wasn't an
                    // extended-length attempt to begin with, or if the
                    // exception isn't one of the two retryable types -- in
                    // which case the normal-length retry below has already
                    // failed too, and its exception propagates as-is. No PII
                    // in this log: only the exception class name, never card
                    // data.
                    if (!extendedSupported || (e !is CardServiceException && e !is IOException)) throw e
                    Log.d(
                        TAG,
                        "extended-length DG2 attempt failed (${e.javaClass.simpleName}); retrying with normal length",
                    )
                    session.close()
                    session = PaceSession(isoDep)
                    paceSession = session
                    val passportService = session.open(can, false)
                    val bytes = session.readFacePhoto(passportService)
                    Log.d(TAG, "DG2 read succeeded on normal-length retry")
                    bytes
                }
            }

            emit(ReadState.ReadingCard)

            val applet = NationalApplet(isoDep, can)
            applet.selectApplicationAndPace()
            applet.verifyPinAndSelectDf(pin)

            val personal = CeiAsn1Decoder.decodePersonal(applet.readEf("0101"))
            val birth = CeiAsn1Decoder.decodeBirth(applet.readEf("0102"))
            val issuer = CeiAsn1Decoder.decodeIssuer(applet.readEf("0104"))
            val currentAddress = CeiAsn1Decoder.decodeAddress(applet.readEf("0106"))
            val temporary = CeiAsn1Decoder.decodeAddressPeriods(applet.readEf("0107"))
            val foreign = CeiAsn1Decoder.decodeAddressPeriods(applet.readEf("0108"))

            val data = CeiData(
                lastName = personal.lastName,
                firstName = personal.firstName,
                gender = personal.gender,
                citizenship = personal.citizenship,
                birthDate = personal.birthDate,
                cnp = personal.cnp,
                faceImage = photo,
                placeOfBirth = birth.placeOfBirth,
                fatherName = null,
                motherName = null,
                documentSerialNo = issuer.documentSerialNo,
                issuingAuthority = issuer.issuingAuthority,
                issuingDate = issuer.issuingDate,
                expiryDate = issuer.expiryDate,
                currentAddress = currentAddress,
                temporaryAddresses = temporary,
                foreignAddresses = foreign,
            )

            emit(ReadState.Finished(data))
        } finally {
            // Only PaceSession needs explicit cleanup (and only when the full,
            // includePhoto=true path ran it at all): NationalApplet's
            // IsoDepCardService.open() is a no-op over an already-connected
            // isoDep (see NationalApplet kdoc), and the tag connection itself is
            // owned by the caller (NfcReaderController). PaceSession.close() (not
            // just `.service?.close()`) is used here because `service` is a
            // lateinit var only assigned after PACE succeeds -- open() failing
            // before that point (wrong CAN, tag lost mid-handshake, no PACEInfo)
            // must still tear down the card connection it partially opened.
            paceSession?.close()
        }
    }.catch { emit(mapError(it)) }.flowOn(Dispatchers.IO)

    /**
     * Pure mapping from a raw exception thrown during [read] to a
     * [ReadState.Error]. Does not distinguish a wrong CAN from other PACE
     * failures for MVP -- both surface as [ReadErrorKind.COMMUNICATION].
     */
    fun mapError(t: Throwable): ReadState.Error = when (t) {
        is WrongPinException -> ReadState.Error(ReadErrorKind.WRONG_PIN, t.retriesLeft)
        is PinBlockedException -> ReadState.Error(ReadErrorKind.PIN_BLOCKED)
        is IOException -> ReadState.Error(ReadErrorKind.CARD_LOST)
        is CardServiceException -> ReadState.Error(ReadErrorKind.COMMUNICATION)
        else -> ReadState.Error(ReadErrorKind.UNKNOWN)
    }

    companion object {
        private const val TAG = "CeiCardReader"
    }
}
