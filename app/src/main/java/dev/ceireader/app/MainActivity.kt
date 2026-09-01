package dev.ceireader.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import dev.ceireader.app.nfc.NfcReaderController
import dev.ceireader.app.ui.CeiApp
import dev.ceireader.app.ui.CeiTheme
import dev.ceireader.app.ui.ReadViewModel

/**
 * Single-activity host: wires the physical NFC adapter (via [NfcReaderController]) to
 * [ReadViewModel] and renders [CeiApp]. Also tracks [NfcAdapter.ACTION_ADAPTER_STATE_CHANGED]
 * while resumed so toggling NFC on/off from Quick Settings while the app is open (re)arms
 * reader mode and updates the entry screen without needing an activity restart.
 */
class MainActivity : ComponentActivity() {

    private val vm: ReadViewModel by viewModels()
    private lateinit var nfcController: NfcReaderController
    private var nfcStateReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcController = NfcReaderController(this)
        vm.updateNfcStatus(nfcController.status())
        setContent {
            CeiTheme {
                CeiApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.updateNfcStatus(nfcController.status())
        nfcController.enable { iso -> vm.onTag(iso) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val newState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                if (newState == NfcAdapter.STATE_ON) {
                    nfcController.enable { iso -> vm.onTag(iso) }
                } else {
                    nfcController.disable()
                }
                vm.updateNfcStatus(nfcController.status())
            }
        }
        nfcStateReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        nfcController.disable()
        nfcStateReceiver?.let { unregisterReceiver(it) }
        nfcStateReceiver = null
    }
}
