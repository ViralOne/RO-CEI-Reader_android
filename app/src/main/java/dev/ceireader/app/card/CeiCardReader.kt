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
 * national applet login + the six personal-data EFs, decoded into a single
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
        // TEMP: perf timing, remove before release.
        val tTotalStart = System.currentTimeMillis()
        var pace1Ms = 0L
        var dg2Ms = 0L
        var dg2Bytes = 0
        var extendedUsed = false
        try {
            var photo: ByteArray? = null

            if (includePhoto) {
                val extendedSupported = isoDep.isExtendedLengthApduSupported()
                extendedUsed = extendedSupported
                var session = PaceSession(isoDep)
                paceSession = session

                // TEMP: perf timing, remove before release.
                val tPace1Start = System.currentTimeMillis()
                var passportService = session.open(can, extendedSupported)
                pace1Ms = System.currentTimeMillis() - tPace1Start

                val tDg2Start = System.currentTimeMillis()
                photo = try {
                    session.readFacePhoto(passportService)
                } catch (e: CardServiceException) {
                    // Safe fallback (task 1): the tag reported extended-length
                    // support but the FIRST DG2 read still failed -- retry the
                    // whole open with the original normal (256) length rather
                    // than surfacing a spurious failure. No PII in this log:
                    // only the exception class name, never card data.
                    if (!extendedSupported) throw e
                    Log.d(
                        PERF_TAG,
                        "PERF dg2 extended-length attempt failed (${e.javaClass.simpleName}); retrying with normal length",
                    )
                    session.close()
                    session = PaceSession(isoDep)
                    paceSession = session
                    extendedUsed = false
                    passportService = session.open(can, false)
                    session.readFacePhoto(passportService)
                }
                dg2Ms = System.currentTimeMillis() - tDg2Start
                dg2Bytes = photo.size
            }

            emit(ReadState.ReadingCard)

            val applet = NationalApplet(isoDep, can)
            // TEMP: perf timing, remove before release.
            val tAppletStart = System.currentTimeMillis()
            applet.selectApplicationAndPace()
            val appletMs = System.currentTimeMillis() - tAppletStart

            val tPinStart = System.currentTimeMillis()
            applet.verifyPinAndSelectDf(pin)
            val pinMs = System.currentTimeMillis() - tPinStart

            val tEfsStart = System.currentTimeMillis()
            val personal = CeiAsn1Decoder.decodePersonal(applet.readEf("0101"))
            val birth = CeiAsn1Decoder.decodeBirth(applet.readEf("0102"))
            val issuer = CeiAsn1Decoder.decodeIssuer(applet.readEf("0104"))
            val currentAddress = CeiAsn1Decoder.decodeAddress(applet.readEf("0106"))
            val temporary = CeiAsn1Decoder.decodeAddressPeriods(applet.readEf("0107"))
            val foreign = CeiAsn1Decoder.decodeAddressPeriods(applet.readEf("0108"))
            val efsMs = System.currentTimeMillis() - tEfsStart

            val totalMs = System.currentTimeMillis() - tTotalStart
            // TEMP: perf timing, remove before release. Durations/byte counts/
            // booleans only -- no field values, no card data.
            Log.d(
                PERF_TAG,
                "PERF photo=$includePhoto pace1=${pace1Ms}ms dg2=${dg2Ms}ms(bytes=$dg2Bytes) " +
                    "applet+pace2=${appletMs}ms pin=${pinMs}ms efs=${efsMs}ms total=${totalMs}ms extended=$extendedUsed",
            )

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
        // TEMP: perf timing, remove before release.
        private const val PERF_TAG = "PERF"
    }
}
