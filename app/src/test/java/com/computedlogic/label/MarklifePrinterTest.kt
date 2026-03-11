package com.computedlogic.label

import android.graphics.Bitmap
import android.graphics.Color
import com.computedlogic.label.printer.MarklifePrinter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MarklifePrinter] – verifies the Marklife packet protocol.
 *
 * Uses Robolectric so android.graphics.Bitmap is available on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarklifePrinterTest {

    // ── Packet count ──────────────────────────────────────────────────────────

    @Test
    fun `non-segmented build produces 5 packets`() {
        // init + header + payload + purge + end
        val packets = MarklifePrinter.buildPackets(whiteBitmap(10), segmented = false)
        assertEquals(5, packets.size)
    }

    @Test
    fun `segmented build produces 7 packets`() {
        // init + header + payload + 4 end-sequence packets
        val packets = MarklifePrinter.buildPackets(whiteBitmap(10), segmented = true)
        assertEquals(7, packets.size)
    }

    // ── Init packet ───────────────────────────────────────────────────────────

    @Test
    fun `first packet is the init sequence 0x10 0xFF 0x40`() {
        val init = MarklifePrinter.buildPackets(whiteBitmap(1))[0]
        assertArrayEquals(byteArrayOf(0x10, 0xFF.b, 0x40), init)
    }

    // ── Header packet ─────────────────────────────────────────────────────────

    @Test
    fun `header is exactly 27 bytes`() {
        val header = MarklifePrinter.buildPackets(whiteBitmap(1))[1]
        assertEquals(27, header.size)
    }

    @Test
    fun `header starts with 15 zero bytes`() {
        val header = MarklifePrinter.buildPackets(whiteBitmap(1))[1]
        for (i in 0 until 15) {
            assertEquals("header[$i] should be 0x00", 0.b, header[i])
        }
    }

    @Test
    fun `header bytes 15-24 match the protocol constants`() {
        val header = MarklifePrinter.buildPackets(whiteBitmap(1))[1]
        // [0x10, 0xFF, 0xF1, 0x02, 0x1D, 0x76, 0x30, 0x00, 0x0C, 0x00]
        val expected = byteArrayOf(0x10, 0xFF.b, 0xF1.b, 0x02, 0x1D, 0x76, 0x30, 0x00, 0x0C, 0x00)
        for (i in expected.indices) {
            assertEquals("header[${15 + i}]", expected[i], header[15 + i])
        }
    }

    @Test
    fun `header encodes label length little-endian at bytes 25-26`() {
        val length = 200   // fits in one byte
        val header = MarklifePrinter.buildPackets(whiteBitmap(length))[1]
        assertEquals((length and 0xFF).b, header[25])  // low byte = 200
        assertEquals(0.b,                  header[26])  // high byte = 0
    }

    @Test
    fun `header encodes multi-byte label length correctly`() {
        val length = 512    // 0x0200 → lo=0x00, hi=0x02
        val header = MarklifePrinter.buildPackets(whiteBitmap(length))[1]
        assertEquals(0x00.b, header[25])
        assertEquals(0x02.b, header[26])
    }

    @Test
    fun `label length 1 encodes as 0x01 0x00`() {
        val header = MarklifePrinter.buildPackets(whiteBitmap(1))[1]
        assertEquals(0x01.b, header[25])
        assertEquals(0x00.b, header[26])
    }

    // ── Payload size ──────────────────────────────────────────────────────────

    @Test
    fun `payload size equals width times 12 (96px divided by 8 bits)`() {
        val width   = 50
        val payload = MarklifePrinter.buildPackets(whiteBitmap(width))[2]
        assertEquals(width * 12, payload.size)
    }

    @Test
    fun `payload size for width 1 is 12 bytes`() {
        val payload = MarklifePrinter.buildPackets(whiteBitmap(1))[2]
        assertEquals(12, payload.size)
    }

    @Test
    fun `payload size for width 200 is 2400 bytes`() {
        val payload = MarklifePrinter.buildPackets(whiteBitmap(200))[2]
        assertEquals(2_400, payload.size)
    }

    // ── Payload content ───────────────────────────────────────────────────────

    @Test
    fun `all-white bitmap produces all-zero payload`() {
        val bmp     = whiteBitmap(5)
        val payload = MarklifePrinter.buildPackets(bmp)[2]
        assertTrue("Expected all zeros for white bitmap",
            payload.all { it == 0.b })
    }

    @Test
    fun `all-black bitmap produces all-0xFF payload`() {
        val bmp = Bitmap.createBitmap(3, 96, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)
        val payload = MarklifePrinter.buildPackets(bmp)[2]
        assertTrue("Expected all 0xFF for black bitmap",
            payload.all { it == 0xFF.b })
        bmp.recycle()
    }

    @Test
    fun `single black pixel at bottom-right of print head encodes to 0x80 in first byte`() {
        // Pixel (x=0, y=95) is at the bottom of the 96-px print head.
        // Encoding: y=0 → invertedY=88, rows 88-95, bit=(row-88).
        // row=95 → bit=7 → byte |= 0x80
        val bmp = whiteBitmap(1)
        bmp.setPixel(0, 95, Color.BLACK)

        val payload = MarklifePrinter.buildPackets(bmp)[2]

        assertEquals(0x80.b, payload[0])               // bit 7 set in first byte
        assertTrue(payload.drop(1).all { it == 0.b })  // rest are 0
        bmp.recycle()
    }

    @Test
    fun `single black pixel at top-left of print head encodes to 0x01 in last byte`() {
        // Pixel (x=0, y=0) is at the top of the print head.
        // y=88 → invertedY=0, rows 0-7, bit=(row-0). row=0 → bit=0 → byte |= 0x01
        // This is the LAST byte of the 12-byte column (bytes[11]).
        val bmp = whiteBitmap(1)
        bmp.setPixel(0, 0, Color.BLACK)

        val payload = MarklifePrinter.buildPackets(bmp)[2]

        assertTrue(payload.take(11).all { it == 0.b }) // bytes 0-10 are zero
        assertEquals(0x01.b, payload[11])              // byte 11: bit 0 set
        bmp.recycle()
    }

    @Test
    fun `second column is independent of first column`() {
        // Only column 1 (x=1) is black; column 0 (x=0) stays white
        val bmp = Bitmap.createBitmap(2, 96, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 0 until 96) bmp.setPixel(1, y, Color.BLACK)

        val payload = MarklifePrinter.buildPackets(bmp)[2]
        // Column 0: all zeros
        assertTrue(payload.take(12).all { it == 0.b })
        // Column 1: all 0xFF
        assertTrue(payload.drop(12).all { it == 0xFF.b })
        bmp.recycle()
    }

    // ── End sequences ─────────────────────────────────────────────────────────

    @Test
    fun `non-segmented purge packet is 0x1B 0x4A 0x5B`() {
        val packets = MarklifePrinter.buildPackets(whiteBitmap(1), segmented = false)
        assertArrayEquals(byteArrayOf(0x1B, 0x4A, 0x5B), packets[3])
    }

    @Test
    fun `non-segmented end packet is 0x10 0xFF 0xF1 0x45`() {
        val packets = MarklifePrinter.buildPackets(whiteBitmap(1), segmented = false)
        assertArrayEquals(byteArrayOf(0x10, 0xFF.b, 0xF1.b, 0x45), packets[4])
    }

    @Test
    fun `segmented first end packet is 0x1D 0x0C 0x10`() {
        val packets = MarklifePrinter.buildPackets(whiteBitmap(1), segmented = true)
        assertArrayEquals(byteArrayOf(0x1D, 0x0C, 0x10), packets[3])
    }

    @Test
    fun `segmented second end packet is 0xFF 0xF1 0x45`() {
        val packets = MarklifePrinter.buildPackets(whiteBitmap(1), segmented = true)
        assertArrayEquals(byteArrayOf(0xFF.b, 0xF1.b, 0x45), packets[4])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** All-white, [width]×96 bitmap (printer's print-head height). */
    private fun whiteBitmap(width: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, 96, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return bmp
    }

    private val Int.b get() = toByte()
}


