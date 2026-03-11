package com.computedlogic.label.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.computedlogic.label.HAlign
import com.computedlogic.label.VAlign
import com.computedlogic.label.ble.BleConstants

/**
 * Renders label content into an [android.graphics.Bitmap] ready for [com.computedlogic.label.printer.MarklifePrinter].
 *
 * Output bitmap:
 *   width  = [labelLengthPx]    (label feed direction, variable)
 *   height = [BleConstants.PRINT_HEIGHT_PX] = 96  (physical print-head, always 12 mm)
 *   white background, black ink
 */
object LabelRenderer {

    /**
     * @param text           Text to render (may be empty for a blank label)
     * @param fontSize       Font size in printer pixels (will be clamped to fit)
     * @param isBold         Bold style
     * @param isItalic       Italic style
     * @param isUnderline    Underline style
     * @param hAlign         Horizontal text alignment within content area
     * @param vAlign         Vertical text alignment within content area
     * @param paddingTopMm   Top padding in millimetres
     * @param paddingBottomMm Bottom padding in millimetres
     * @param paddingLeftMm  Left padding in millimetres
     * @param paddingRightMm Right padding in millimetres
     * @param labelLengthPx  Label feed-length in printer pixels (default 200 ≈ 25 mm at 203 dpi)
     */
    fun render(
        text: String,
        fontSize: Int,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        hAlign: HAlign,
        vAlign: VAlign,
        paddingTopMm: Int    = 0,
        paddingBottomMm: Int = 0,
        paddingLeftMm: Int   = 0,
        paddingRightMm: Int  = 0,
        labelLengthPx: Int   = 0   // 0 = auto-size to fit text
    ): Bitmap {
        val printHeight = BleConstants.PRINT_HEIGHT_PX   // 96 px tall, always
        val dpm         = BleConstants.DOTS_PER_MM       // 8 px per mm

        val padTop    = (paddingTopMm    * dpm).coerceIn(0, printHeight - 1)
        val padBottom = (paddingBottomMm * dpm).coerceIn(0, printHeight - 1)
        val contentH = (printHeight - padTop - padBottom).coerceAtLeast(1)

        val typeface = Typeface.create(
            Typeface.DEFAULT,
            when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold             -> Typeface.BOLD
                isItalic           -> Typeface.ITALIC
                else               -> Typeface.NORMAL
            }
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color           = Color.BLACK
            this.typeface   = typeface
            textSize        = fontSize.toFloat().coerceAtMost(contentH.toFloat())
            isUnderlineText = isUnderline
            textAlign = when (hAlign) {
                HAlign.LEFT   -> Paint.Align.LEFT
                HAlign.CENTER -> Paint.Align.CENTER
                HAlign.RIGHT  -> Paint.Align.RIGHT
            }
        }

        // Only split on explicit newlines — no word wrapping
        val lines = if (text.isBlank()) listOf("") else text.split("\n")

        // Compute padding in px (preliminary, may adjust if auto-sizing)
        val padLeftPx  = paddingLeftMm  * dpm
        val padRightPx = paddingRightMm * dpm

        // Determine label width: auto-size if labelLengthPx <= 0
        val finalWidth: Int
        if (labelLengthPx > 0) {
            finalWidth = labelLengthPx
        } else {
            val maxTextWidth = lines.maxOf { paint.measureText(it) }.toInt()
            finalWidth = (maxTextWidth + padLeftPx + padRightPx + 2).coerceAtLeast(200)
        }

        val padLeft  = padLeftPx.coerceIn(0, finalWidth - 1)
        val padRight = padRightPx.coerceIn(0, finalWidth - 1)
        val contentW = (finalWidth - padLeft - padRight).coerceAtLeast(1)

        val bmp    = Bitmap.createBitmap(finalWidth, printHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        if (text.isBlank()) return bmp

        val fm     = paint.fontMetrics
        val lineH  = fm.descent - fm.ascent
        val totalH = lines.size * lineH

        val textX = when (hAlign) {
            HAlign.LEFT   -> padLeft.toFloat()
            HAlign.CENTER -> padLeft + contentW / 2f
            HAlign.RIGHT  -> (padLeft + contentW).toFloat()
        }

        // Baseline of first line
        val startY = when (vAlign) {
            VAlign.TOP    -> padTop.toFloat() - fm.ascent
            VAlign.CENTER -> padTop + (contentH - totalH) / 2f - fm.ascent
            VAlign.BOTTOM -> padTop + contentH - totalH - fm.ascent
        }

        lines.forEachIndexed { i, line ->
            canvas.drawText(line, textX, startY + i * lineH, paint)
        }

        return bmp
    }
}

