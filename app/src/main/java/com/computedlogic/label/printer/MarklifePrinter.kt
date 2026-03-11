package com.computedlogic.label.printer

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Marklife P12 / P15 / L13 packet encoder.
 *
 * The [bitmap] must have:
 *   width  = label feed length in pixels  (variable, e.g. 200 px ≈ 25 mm)
 *   height = PRINT_WIDTH_PX = 96          (physical print-head width, always 12 mm)
 */
object MarklifePrinter {

    /**
     * Build the full ordered list of byte arrays that must be written to the
     * printer characteristic (in sequence).
     *
     * @param bitmap      96-px-tall bitmap produced by [com.computedlogic.label.render.LabelRenderer]
     * @param segmented   `true` for continuous / segmented paper rolls;
     *                    `false` (default) for fixed-length labels
     */
    fun buildPackets(bitmap: Bitmap, segmented: Boolean = false): List<ByteArray> {
        val labelLengthPx = bitmap.width        // feed direction
        val payload       = encodeToPayload(bitmap)

        return buildList {
            // 1. Initialisation
            add(byteArrayOf(0x10, 0xff.b, 0x40))

            // 2. Header  (15 × 0x00 + dimension/mode bytes)
            add(byteArrayOf(
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,   // 15 padding zeros
                0x10, 0xff.b, 0xf1.b, 0x02, 0x1d,
                0x76,
                0x30, 0x00,
                0x0c, 0x00,
                (labelLengthPx and 0xff).b,
                ((labelLengthPx shr 8) and 0xff).b
            ))

            // 3. Bitmap payload
            add(payload)

            if (segmented) {
                // Segmented / continuous paper end sequence
                add(byteArrayOf(0x1d, 0x0c, 0x10))
                add(byteArrayOf(0xff.b, 0xf1.b, 0x45))
                add(byteArrayOf(0x10, 0xff.b, 0x40))
                add(byteArrayOf(0x10, 0xff.b, 0x40))
            } else {
                // Fixed label end sequence
                add(byteArrayOf(0x1b, 0x4a, 0x5b))          // purge
                add(byteArrayOf(0x10, 0xff.b, 0xf1.b, 0x45)) // end
            }
        }
    }

    // ── Bitmap → payload encoding ─────────────────────────────────────────────

    /**
     * Encodes a bitmap into the Marklife payload format.
     *
     * For each column x (0 .. labelLength-1) in the feed direction,
     * emit 12 bytes representing the 96 print-head pixels bottom-to-top,
     * packed LSB-first into 8-bit groups.
     *
     * bitmap.width  = labelLengthPx  (feed direction)
     * bitmap.height = 96             (print head, always)
     */
    private fun encodeToPayload(bitmap: Bitmap): ByteArray {
        val width  = bitmap.width          // label feed length
        val height = bitmap.height         // must be PRINT_WIDTH_PX (96)

        val bytes = ArrayList<Byte>(width * (height / 8))

        for (x in 0 until width) {
            var y = 0
            while (y < height) {
                val invertedY = height - 8 - y   // bottom-to-top scan order
                var byte = 0
                for (bit in 0 until 8) {
                    val row = invertedY + bit
                    if (row in 0 until height) {
                        val pixel = bitmap.getPixel(x, row)
                        // Dark pixel (luminance < 128) → print dot
                        if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < 128) {
                            byte = byte or (1 shl bit)
                        }
                    }
                }
                bytes.add(byte.toByte())
                y += 8
            }
        }

        return bytes.toByteArray()
    }

    // Convenience extension to avoid verbose .toByte() calls for hex literals
    private val Int.b get() = toByte()
}


