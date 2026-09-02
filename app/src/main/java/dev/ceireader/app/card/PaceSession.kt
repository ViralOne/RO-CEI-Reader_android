package dev.ceireader.app.card

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.APDUWrapper
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG2File

/**
 * Opens a PACE(CAN)-secured session against the ICAO applet over an already
 * connected [IsoDep] tag, using JMRTD's [PassportService]. The opened service
 * is retained on [service] so a later step (national eID applet selection +
 * PIN verification, driven through the same secure channel) can reuse it.
 */
class PaceSession(private val isoDep: IsoDep) {

    /** The [PassportService] established by [open]. Reused by later steps (Task 6). */
    lateinit var service: PassportService
        private set

    // Tracked as soon as they're created inside [open] (before ps.open()/doPACE), so
    // [close] can tear down the card connection even if [open] only partially
    // completed (e.g. wrong CAN, tag lost during handshake, no PACEInfo) -- i.e.
    // before [service] itself is ever assigned.
    private var openedCardService: IsoDepCardService? = null
    private var openedPassportService: PassportService? = null

    /**
     * Connects to the card, reads EF.CardAccess to find the PACEInfo, runs
     * PACE with the given CAN, then selects the ICAO applet under the new
     * secure channel. Returns the opened [PassportService].
     *
     * [useExtendedLength] picks the transceive length used for the
     * SM-wrapped applet-file-system reads that follow (i.e. the later DG2
     * read): when true, [PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH] plus
     * a [maxBlockSize][computeMaxBlockSize] sized to the tag's own transceive
     * limit, cutting the number of READ BINARY round trips; when false, the
     * original [PassportService.NORMAL_MAX_TRANCEIVE_LENGTH] /
     * [PassportService.DEFAULT_MAX_BLOCKSIZE] pair. This does NOT affect the
     * PACE handshake itself -- [PassportService]'s 5-arg constructor always
     * runs PACE at the normal 256-byte length internally regardless of what's
     * passed here (verified against the resolved jmrtd-0.8.8 jar/source).
     */
    fun open(can: String, useExtendedLength: Boolean): PassportService {
        val cardService = IsoDepCardService(isoDep)
        openedCardService = cardService
        val maxTranceiveLength = if (useExtendedLength) {
            PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH
        } else {
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH
        }
        val maxBlockSize = if (useExtendedLength) {
            computeMaxBlockSize(isoDep)
        } else {
            PassportService.DEFAULT_MAX_BLOCKSIZE
        }
        val ps = PassportService(
            cardService,
            maxTranceiveLength,
            maxBlockSize,
            false,
            false,
        )
        openedPassportService = ps
        ps.open()

        val cardAccessFile = CardAccessFile(ps.getInputStream(PassportService.EF_CARD_ACCESS))
        val paceInfo = cardAccessFile.securityInfos.filterIsInstance<PACEInfo>().firstOrNull()
            ?: throw CardServiceException("EF.CardAccess did not contain a PACEInfo")

        val paceKey = PACEKeySpec.createCANKey(can)
        ps.doPACE(
            paceKey,
            paceInfo.objectIdentifier,
            PACEInfo.toParameterSpec(paceInfo.parameterId),
            null,
        )
        ps.sendSelectApplet(true)

        service = ps
        return ps
    }

    /** Reads DG2 and returns the raw bytes of the first face image. */
    fun readFacePhoto(service: PassportService): ByteArray {
        val dg2 = DG2File(service.getInputStream(PassportService.EF_DG2))
        val faceInfo = dg2.faceInfos.firstOrNull()
            ?: throw CardServiceException("DG2 contained no FaceInfo")
        val faceImageInfo = faceInfo.faceImageInfos.firstOrNull()
            ?: throw CardServiceException("DG2 FaceInfo contained no FaceImageInfo")
        return faceImageInfo.imageInputStream.readBytes()
    }

    /**
     * Exposes the secure-messaging wrapper established by [open] so Task 6 can
     * drive the national applet through the same open, PACE-secured channel.
     */
    fun wrapper(): APDUWrapper = service.wrapper

    /**
     * Tears down whatever card connection [open] managed to create, even if it
     * only got partway through (wrong CAN, tag lost during handshake, missing
     * PACEInfo, ...) before [service] itself was ever assigned. Safe to call
     * even if [open] was never called, failed immediately, or already
     * succeeded; never throws.
     *
     * [PassportService.close] delegates to the underlying [IsoDepCardService]
     * it wraps, so calling it alone (when available) closes the card
     * connection exactly once; [openedCardService] is only used as a fallback
     * for the (practically unreachable) case where [openedPassportService]
     * itself was never assigned.
     */
    fun close() {
        try {
            val ps = openedPassportService
            if (ps != null) {
                ps.close()
            } else {
                openedCardService?.close()
            }
        } catch (_: Exception) {
            // Best-effort cleanup: a close failure must not mask/replace
            // whatever read result the caller is already handling.
        }
    }

    companion object {
        /**
         * Headroom subtracted from [IsoDep.getMaxTransceiveLength] before using
         * it as the extended-length READ BINARY block size: the response is
         * secure-messaging wrapped (DO87 ciphertext TLV + DO99 status + DO8E
         * MAC TLV, plus CBC padding), so the plaintext payload capacity is a
         * bit smaller than the tag's raw transceive limit. Conservative rather
         * than tight, since overshooting would fail the transceive outright.
         */
        private const val EXTENDED_SM_OVERHEAD_BYTES = 64

        /**
         * Floor for the computed block size: small enough to stay well under
         * any device's [IsoDep.getMaxTransceiveLength], but large enough that
         * a chained READ BINARY still makes forward progress.
         */
        private const val MIN_BLOCK_SIZE = 64

        /**
         * Largest READ BINARY block size safe to request when extended-length
         * APDUs are in play: bounded above by both what the hardware/NFC stack
         * can move in one [IsoDep] transceive (minus SM headroom) and by
         * [PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH] (JMRTD's own
         * ceiling). Unlike a naive `coerceIn(DEFAULT_MAX_BLOCKSIZE, ...)`, this
         * must NEVER be pushed up to [PassportService.DEFAULT_MAX_BLOCKSIZE]
         * (223) when the device's real transceive limit is smaller than that
         * (many NFC controllers report ~253-261 bytes total, i.e. a hardware
         * limit well under 223 once SM overhead is subtracted) -- doing so
         * would request a block larger than the transceive can actually carry
         * and fail the read outright. So this only ever picks the SMALLER of
         * the desired block and the hardware limit, with [MIN_BLOCK_SIZE] as a
         * sane floor.
         */
        private fun computeMaxBlockSize(isoDep: IsoDep): Int {
            val hardwareLimit = isoDep.maxTransceiveLength - EXTENDED_SM_OVERHEAD_BYTES
            val desiredBlock = PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH
            return minOf(desiredBlock, hardwareLimit).coerceAtLeast(MIN_BLOCK_SIZE)
        }
    }
}
