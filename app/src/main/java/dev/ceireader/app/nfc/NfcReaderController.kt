package dev.ceireader.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import dev.ceireader.app.model.NfcStatus

/**
 * Wraps [NfcAdapter] reader mode so callers get an already-connected [IsoDep]
 * whenever a tag is discovered, without dealing with foreground-dispatch intents.
 *
 * [enable] and [disable] are idempotent: they can be called repeatedly (e.g. once
 * from `onResume` and again from an `ACTION_ADAPTER_STATE_CHANGED` broadcast) without
 * double-registering or leaking the reader-mode callback.
 */
class NfcReaderController(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var readerModeEnabled = false

    /** Current adapter state, for driving the entry screen's NFC messaging. */
    fun status(): NfcStatus = when {
        adapter == null -> NfcStatus.NO_HARDWARE
        !adapter.isEnabled -> NfcStatus.DISABLED
        else -> NfcStatus.ENABLED
    }

    /** Arms reader mode if NFC is available and on; a no-op if already armed or NFC is off/absent. */
    fun enable(onTag: (IsoDep) -> Unit) {
        if (adapter == null || !adapter.isEnabled || readerModeEnabled) return
        adapter.enableReaderMode(
            activity,
            { tag ->
                val iso = IsoDep.get(tag) ?: return@enableReaderMode
                iso.timeout = 8000
                onTag(iso)
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000) },
        )
        readerModeEnabled = true
    }

    /** Disarms reader mode; a no-op if it isn't currently armed. */
    fun disable() {
        if (!readerModeEnabled) return
        adapter?.disableReaderMode(activity)
        readerModeEnabled = false
    }
}
