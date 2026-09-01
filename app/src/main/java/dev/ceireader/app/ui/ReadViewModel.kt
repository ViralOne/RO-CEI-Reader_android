package dev.ceireader.app.ui

import android.nfc.tech.IsoDep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ceireader.app.card.CeiCardReader
import dev.ceireader.app.model.NfcStatus
import dev.ceireader.app.model.ReadState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the CAN/PIN the user typed on the entry screen and drives
 * [CeiCardReader.read] whenever a tag is presented, exposing the resulting
 * [ReadState] sequence for [CeiApp] to render.
 */
class ReadViewModel : ViewModel() {

    private val reader = CeiCardReader()

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()

    var can by mutableStateOf("")
    var pin by mutableStateOf("")

    /**
     * Reflects [dev.ceireader.app.nfc.NfcReaderController.status]; kept in sync by
     * [dev.ceireader.app.MainActivity] on resume and on NFC adapter-state broadcasts,
     * so the entry screen never promises a read the current NFC state can't honor.
     */
    var nfcStatus by mutableStateOf(NfcStatus.ENABLED)
        private set

    // Guards against a second tag callback starting a concurrent read while one is
    // already in flight -- reader mode can, in principle, fire again before the
    // in-progress read finishes and the UI leaves the reading screen.
    private val isProcessing = AtomicBoolean(false)

    fun updateNfcStatus(status: NfcStatus) {
        nfcStatus = status
    }

    /**
     * Called by [dev.ceireader.app.nfc.NfcReaderController] whenever a tag is discovered.
     *
     * A read is only started from [ReadState.Idle]. If results or an error are already on
     * screen (or a read is [Started]/[ReadingCard]), an incidental tag tap must NOT silently
     * kick off a new read -- the user has to explicitly press "Citește din nou" / "Reîncearcă"
     * ([reset]) to get back to [ReadState.Idle] first.
     */
    fun onTag(isoDep: IsoDep) {
        if (_state.value != ReadState.Idle) return // results/error on screen (or read in flight); ignore the tap.
        if (!isProcessing.compareAndSet(false, true)) return // a read is already in progress; ignore.
        viewModelScope.launch {
            try {
                reader.read(isoDep, can, pin).collect { newState ->
                    _state.value = newState
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    /** Returns to the entry screen, ready for another tap. */
    fun reset() {
        _state.value = ReadState.Idle
    }
}
