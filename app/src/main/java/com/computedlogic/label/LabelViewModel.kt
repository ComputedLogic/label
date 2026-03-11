package com.computedlogic.label

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.computedlogic.label.ble.BleManager
import com.computedlogic.label.printer.MarklifePrinter
import com.computedlogic.label.render.LabelRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parameters captured from the UI when the user taps Print!
 */
data class PrintParams(
    val text: String,
    val fontSize: Int,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val hAlign: HAlign,
    val vAlign: VAlign,
    val paddingTopMm: Int,
    val paddingBottomMm: Int,
    val paddingLeftMm: Int,
    val paddingRightMm: Int,
    val copies: Int,
    val labelLengthPx: Int = 0,   // 0 = auto-size to fit text
    val segmented: Boolean = false
)

class LabelViewModel(app: Application) : AndroidViewModel(app) {

    val bleManager = BleManager(app)
    val bleState: StateFlow<BleManager.State> = bleManager.state

    // ── Bluetooth scanning ────────────────────────────────────────────────────

    fun startScan()  { bleManager.startScan() }
    fun stopScan()   { bleManager.stopScan() }

    // ── Connect only (no print) ───────────────────────────────────────────────

    fun connectOnly(device: BluetoothDevice) {
        viewModelScope.launch {
            bleManager.connect(device)
        }
    }

    // ── Disconnect ─────────────────────────────────────────────────────────────

    fun disconnect() {
        bleManager.disconnect()
    }

    // ── Print (auto-reconnect if needed) ──────────────────────────────────────

    fun print(params: PrintParams) {
        viewModelScope.launch {
            // If the printer dropped the connection (common after print), reconnect first
            if (!bleManager.isConnected) {
                val reconnected = bleManager.reconnect()
                if (!reconnected) return@launch
            }
            sendPrintJob(params)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun sendPrintJob(params: PrintParams) {
        // Render + encode on a background thread
        val packets = withContext(Dispatchers.Default) {
            val bmp = LabelRenderer.render(
                text            = params.text,
                fontSize        = params.fontSize,
                isBold          = params.isBold,
                isItalic        = params.isItalic,
                isUnderline     = params.isUnderline,
                hAlign          = params.hAlign,
                vAlign          = params.vAlign,
                paddingTopMm    = params.paddingTopMm,
                paddingBottomMm = params.paddingBottomMm,
                paddingLeftMm   = params.paddingLeftMm,
                paddingRightMm  = params.paddingRightMm,
                labelLengthPx   = params.labelLengthPx
            )
            MarklifePrinter.buildPackets(bmp, params.segmented).also { bmp.recycle() }
        }

        // Send each copy with a 500 ms gap between them
        for (i in 0 until params.copies) {
            val ok = bleManager.print(packets)
            if (!ok) break
            if (i < params.copies - 1) delay(500)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
