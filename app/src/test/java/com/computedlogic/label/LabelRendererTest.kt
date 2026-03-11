package com.computedlogic.label

import android.graphics.Bitmap
import android.graphics.Color
import com.computedlogic.label.render.LabelRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelRendererTest {

    // Dimensions

    @Test fun outputHeightIsAlways96px() {
        val bmp = render("Hello"); assertEquals(96, bmp.height); bmp.recycle()
    }

    @Test fun defaultOutputWidthIs200px() {
        val bmp = render("Hello"); assertEquals(200, bmp.width); bmp.recycle()
    }

    @Test fun customLabelLengthReflectedInWidth() {
        val bmp = render("Hi", labelLengthPx = 300)
        assertEquals(300, bmp.width); assertEquals(96, bmp.height); bmp.recycle()
    }

    @Test fun minimumLabelLengthOneDoesNotCrash() {
        val bmp = render("X", labelLengthPx = 1)
        assertEquals(1, bmp.width); assertEquals(96, bmp.height); bmp.recycle()
    }

    @Test fun largeLabelLength1000pxProducesCorrectSize() {
        val bmp = render("Wide", labelLengthPx = 1000)
        assertEquals(1000, bmp.width); assertEquals(96, bmp.height); bmp.recycle()
    }

    // Bitmap config

    @Test fun outputBitmapConfigIsARGB8888() {
        val bmp = render("Test")
        assertEquals(Bitmap.Config.ARGB_8888, bmp.config); bmp.recycle()
    }

    // Blank text

    @Test fun blankTextReturnsAllWhiteBitmap() {
        val bmp = render("")
        for (x in 0 until bmp.width) for (y in 0 until bmp.height) {
            val p = bmp.getPixel(x, y)
            assertTrue(Color.red(p) > 240 && Color.green(p) > 240 && Color.blue(p) > 240)
        }
        bmp.recycle()
    }

    @Test fun whitespaceOnlyTextReturnsWhiteBitmap() {
        val bmp = render("   ")
        for (x in 0 until bmp.width step 10) assertTrue(Color.red(bmp.getPixel(x, 48)) > 240)
        bmp.recycle()
    }

    // Styles

    @Test fun boldTextRendersWithoutCrash() {
        val bmp = render("Bold!", isBold = true); assertNotNull(bmp); assertEquals(96, bmp.height); bmp.recycle()
    }

    @Test fun italicTextRendersWithoutCrash() {
        val bmp = render("Italic!", isItalic = true); assertNotNull(bmp); bmp.recycle()
    }

    @Test fun underlineTextRendersWithoutCrash() {
        val bmp = render("Under", isUnderline = true); assertNotNull(bmp); bmp.recycle()
    }

    @Test fun boldItalicCombinedRendersWithoutCrash() {
        val bmp = render("BI", isBold = true, isItalic = true); assertNotNull(bmp); bmp.recycle()
    }

    // All alignments

    @Test fun allNineAlignmentCombinationsRenderWithoutCrash() {
        for (h in HAlign.entries) for (v in VAlign.entries) {
            val bmp = render("X", hAlign = h, vAlign = v)
            assertNotNull(bmp); assertEquals(96, bmp.height); bmp.recycle()
        }
    }

    // Padding

    @Test fun paddingDoesNotAlterBitmapDimensions() {
        val padded = render("Pad", padTop = 1, padBottom = 1, padLeft = 1, padRight = 1)
        val plain  = render("Pad")
        assertEquals(plain.width, padded.width); assertEquals(plain.height, padded.height)
        padded.recycle(); plain.recycle()
    }

    @Test fun excessivePaddingDoesNotCrash() {
        val bmp = render("X", padTop = 100, padBottom = 100); assertNotNull(bmp); bmp.recycle()
    }

    // Pipeline

    @Test fun renderedBitmapCanBePassedToMarklifePrinterWithoutCrash() {
        val bmp = render("Pipeline test")
        val packets = com.computedlogic.label.printer.MarklifePrinter.buildPackets(bmp)
        assertNotNull(packets); assertTrue(packets.isNotEmpty()); bmp.recycle()
    }

    @Test fun renderThenEncodeProducesPayloadOfWidthTimes12Bytes() {
        val len = 150
        val bmp = render("Payload", labelLengthPx = len)
        val payload = com.computedlogic.label.printer.MarklifePrinter.buildPackets(bmp)[2]
        assertEquals(len * 12, payload.size); bmp.recycle()
    }

    // Helper

    private fun render(
        text: String = "Test", fontSize: Int = 24,
        isBold: Boolean = false, isItalic: Boolean = false, isUnderline: Boolean = false,
        hAlign: HAlign = HAlign.LEFT, vAlign: VAlign = VAlign.TOP,
        padTop: Int = 0, padBottom: Int = 0, padLeft: Int = 0, padRight: Int = 0,
        labelLengthPx: Int = 200,
    ): Bitmap = LabelRenderer.render(
        text = text, fontSize = fontSize, isBold = isBold, isItalic = isItalic,
        isUnderline = isUnderline, hAlign = hAlign, vAlign = vAlign,
        paddingTopMm = padTop, paddingBottomMm = padBottom,
        paddingLeftMm = padLeft, paddingRightMm = padRight, labelLengthPx = labelLengthPx,
    )
}
