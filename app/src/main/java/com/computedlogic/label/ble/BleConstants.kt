package com.computedlogic.label.ble

import java.util.UUID

object BleConstants {
    /** Primary service for printing (Marklife P12 / P15 / L13). */
    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")

    /** Writable characteristic used to receive print data. */
    val CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

    /** Secondary service used for reading printer info (battery, firmware …). */
    val INFO_SERVICE_UUID: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")

    /** BLE device name prefixes for supported models. */
    val SUPPORTED_PREFIXES = listOf("P12_", "P15_", "L13_")

    /** Physical print-head width in pixels (12 mm × 8 dpm = 96 px). */
    const val PRINT_HEIGHT_PX = 96

    /** Printer dots per millimetre at 203 dpi. */
    const val DOTS_PER_MM = 8

    /** Requested BLE MTU.  Usable payload = MTU – 3. */
    const val REQUEST_MTU = 256

    /** Inter-write delay in milliseconds. */
    const val CHUNK_DELAY_MS = 30L
}

