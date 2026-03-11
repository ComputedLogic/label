package com.computedlogic.label

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM tests for [PrintParams].
 * No Android runtime required – these run entirely on the JVM.
 */
class PrintParamsTest {

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `default labelLengthPx is 200`() {
        assertEquals(200, minimal().labelLengthPx)
    }

    @Test
    fun `default segmented flag is false`() {
        assertFalse(minimal().segmented)
    }

    // ── Field round-trips ─────────────────────────────────────────────────────

    @Test
    fun `text is preserved`() {
        assertEquals("Hello Printer", minimal(text = "Hello Printer").text)
    }

    @Test
    fun `fontSize is preserved`() {
        assertEquals(72, minimal(fontSize = 72).fontSize)
    }

    @Test
    fun `copies field is preserved`() {
        assertEquals(5, minimal(copies = 5).copies)
    }

    @Test
    fun `padding fields are preserved`() {
        val p = minimal(padTop = 2, padBottom = 3, padLeft = 1, padRight = 4)
        assertEquals(2, p.paddingTopMm)
        assertEquals(3, p.paddingBottomMm)
        assertEquals(1, p.paddingLeftMm)
        assertEquals(4, p.paddingRightMm)
    }

    @Test
    fun `style flags are preserved`() {
        val p = minimal(bold = true, italic = true, underline = false)
        assertTrue(p.isBold)
        assertTrue(p.isItalic)
        assertFalse(p.isUnderline)
    }

    @Test
    fun `alignment fields are preserved`() {
        val p = minimal(hAlign = HAlign.CENTER, vAlign = VAlign.BOTTOM)
        assertEquals(HAlign.CENTER, p.hAlign)
        assertEquals(VAlign.BOTTOM, p.vAlign)
    }

    @Test
    fun `custom labelLengthPx overrides default`() {
        val p = minimal().copy(labelLengthPx = 400)
        assertEquals(400, p.labelLengthPx)
    }

    @Test
    fun `segmented true overrides default`() {
        val p = minimal().copy(segmented = true)
        assertTrue(p.segmented)
    }

    // ── Data-class behaviour ──────────────────────────────────────────────────

    @Test
    fun `copy leaves original unchanged`() {
        val original = minimal(copies = 1)
        val copy     = original.copy(copies = 9, labelLengthPx = 500)
        assertEquals(9,   copy.copies)
        assertEquals(500, copy.labelLengthPx)
        assertEquals(1,   original.copies)        // original intact
        assertEquals(200, original.labelLengthPx)
    }

    @Test
    fun `two params with same values are equal`() {
        assertEquals(minimal(), minimal())
    }

    @Test
    fun `two params differing in copies are not equal`() {
        assertNotEquals(minimal(copies = 1), minimal(copies = 2))
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun minimal(
        text: String      = "Label",
        fontSize: Int     = 48,
        bold: Boolean     = false,
        italic: Boolean   = false,
        underline: Boolean = false,
        hAlign: HAlign    = HAlign.LEFT,
        vAlign: VAlign    = VAlign.TOP,
        padTop: Int = 0, padBottom: Int = 0, padLeft: Int = 0, padRight: Int = 0,
        copies: Int = 1,
    ) = PrintParams(
        text            = text,
        fontSize        = fontSize,
        isBold          = bold,
        isItalic        = italic,
        isUnderline     = underline,
        hAlign          = hAlign,
        vAlign          = vAlign,
        paddingTopMm    = padTop,
        paddingBottomMm = padBottom,
        paddingLeftMm   = padLeft,
        paddingRightMm  = padRight,
        copies          = copies,
    )
}

