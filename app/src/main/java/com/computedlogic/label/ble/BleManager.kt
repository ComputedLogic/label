package com.computedlogic.label.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "BleManager"
private const val CONNECT_TIMEOUT_MS = 15_000L

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    // ── Public state ──────────────────────────────────────────────────────────

    sealed class State {
        object Idle : State()
        object Scanning : State()
        data class DevicesFound(val devices: List<BluetoothDevice>) : State()
        data class Connecting(val device: BluetoothDevice) : State()
        data class Print(val device: BluetoothDevice) : State()
        data class Printing(val progress: Int) : State()
        data class PrintSuccess(val device: BluetoothDevice) : State()   // FIX #3: retain device
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isConnected: Boolean
        get() = gatt != null && printChar != null

    // ── BLE internals ─────────────────────────────────────────────────────────

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var gatt: BluetoothGatt? = null
    private var printChar: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null     // FIX #3: keep device ref
    private var mtuPayload = 20

    private val foundDevices = mutableListOf<BluetoothDevice>()

    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null

    // ── Scanning ──────────────────────────────────────────────────────────────

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _state.value = State.Error("Bluetooth adapter not available")
            return
        }
        foundDevices.clear()
        _state.value = State.Scanning
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        if (_state.value is State.Scanning) {
            _state.value = if (foundDevices.isEmpty()) State.Idle
                           else State.DevicesFound(foundDevices.toList())
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (BleConstants.SUPPORTED_PREFIXES.none { name.startsWith(it) }) return
            if (foundDevices.any { it.address == result.device.address }) return
            foundDevices.add(result.device)
            _state.value = State.DevicesFound(foundDevices.toList())
        }
        override fun onScanFailed(errorCode: Int) {
            _state.value = State.Error("BLE scan failed (code $errorCode)")
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    suspend fun connect(device: BluetoothDevice): Boolean {
        // If already connected to this device, reuse
        if (gatt != null && printChar != null && connectedDevice?.address == device.address) {
            Log.d(TAG, "Already connected to ${device.address}, reusing")
            _state.value = State.Print(device)
            return true
        }

        // Clean up any previous connection first
        closeGattQuietly()

        stopScan()
        _state.value = State.Connecting(device)
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        // FIX #7: connection timeout
        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            deferred.await()
        }

        if (result == null) {
            // Timed out
            connectDeferred = null
            closeGattQuietly()
            _state.value = State.Error("Connection timed out")
            return false
        }

        if (result) {
            connectedDevice = device   // FIX #3: remember the device
        }
        return result
    }

    fun disconnect() {
        closeGattQuietly()
        connectedDevice = null
        _state.value = State.Idle
    }

    /** FIX #1: Properly close GATT and cancel pending deferreds without leaking. */
    private fun closeGattQuietly() {
        writeDeferred?.complete(false)     // FIX #2: unblock any hanging write
        writeDeferred = null
        connectDeferred?.complete(false)
        connectDeferred = null
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}   // FIX #1: always close()
        gatt = null
        printChar = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    g.requestMtu(BleConstants.REQUEST_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // FIX #1: close the GATT client to free the slot
                    try { g.close() } catch (_: Exception) {}

                    printChar = null
                    gatt = null
                    // Note: we do NOT null connectedDevice — we keep it for reconnection

                    // FIX #2: unblock any pending write so the coroutine doesn't hang
                    writeDeferred?.complete(false)
                    writeDeferred = null

                    connectDeferred?.complete(false)
                    connectDeferred = null

                    // FIX #6: don't clobber PrintSuccess — the UI needs to see it
                    val current = _state.value
                    if (current !is State.PrintSuccess && current !is State.Error) {
                        _state.value = State.Error("Printer disconnected")
                    }
                    Log.d(TAG, "GATT closed, device reference kept for reconnection")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuPayload = if (status == BluetoothGatt.GATT_SUCCESS) mtu - 3 else 20
            Log.d(TAG, "MTU negotiated: $mtu (payload $mtuPayload bytes)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Error("Service discovery failed (status $status)")
                connectDeferred?.complete(false)
                connectDeferred = null
                return
            }
            val char = g.getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(BleConstants.CHAR_UUID)
            if (char == null) {
                _state.value = State.Error("Printer characteristic not found")
                connectDeferred?.complete(false)
                connectDeferred = null
                return
            }
            printChar = char
            _state.value = State.Print(g.device)
            connectDeferred?.complete(true)
            connectDeferred = null
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            writeDeferred = null
        }
    }

    // ── Printing ──────────────────────────────────────────────────────────────

    suspend fun print(packets: List<ByteArray>): Boolean {
        val char = printChar
        val g    = gatt
        if (char == null || g == null) {
            _state.value = State.Error("Printer not connected")
            return false
        }

        val totalChunks = packets.sumOf { chunkCount(it) }
        var sent = 0
        _state.value = State.Printing(0)

        for (packet in packets) {
            var offset = 0
            while (offset < packet.size) {
                val end   = minOf(offset + mtuPayload, packet.size)
                val chunk = packet.copyOfRange(offset, end)

                val ok = writeChunk(g, char, chunk)
                if (!ok) {
                    _state.value = State.Error("Write failed at chunk $sent")
                    return false
                }
                sent++
                _state.value = State.Printing((sent * 100) / totalChunks)
                delay(BleConstants.CHUNK_DELAY_MS)
                offset = end
            }
        }

        // FIX #3: pass connectedDevice so UI can reconnect to same printer
        _state.value = State.PrintSuccess(connectedDevice ?: g.device)
        return true
    }

    /** FIX #5: Reconnect to the last known device (e.g. after printer-initiated disconnect). */
    suspend fun reconnect(): Boolean {
        val device = connectedDevice ?: return false
        Log.d(TAG, "Attempting reconnection to ${device.address}")
        return connect(device)
    }

    private suspend fun writeChunk(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        chunk: ByteArray
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        writeDeferred = deferred

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = chunk
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }

        return deferred.await()
    }

    private fun chunkCount(data: ByteArray): Int =
        (data.size + mtuPayload - 1) / mtuPayload
}
