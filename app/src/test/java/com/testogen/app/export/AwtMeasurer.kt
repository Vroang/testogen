package com.testogen.app.export

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.text.AttributedString

/** Измерение настоящим Times New Roman (на компьютере) — для сверки с Word/LibreOffice. */
class AwtMeasurer : TextMeasurer {
    private val frc = FontRenderContext(null, true, true)
    override fun lines(text: String, widthPt: Float, sizePt: Float, bold: Boolean): Int {
        if (text.isEmpty()) return 1
        val font = Font("Times New Roman", if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(sizePt)
        val s = AttributedString(text).apply { addAttribute(TextAttribute.FONT, font) }
        val m = LineBreakMeasurer(s.iterator, frc)
        var n = 0
        while (m.position < text.length) {
            m.nextLayout(widthPt)
            n++
        }
        return n
    }
}
