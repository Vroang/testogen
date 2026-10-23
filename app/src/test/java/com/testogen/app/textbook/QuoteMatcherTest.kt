package com.testogen.app.textbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteMatcherTest {

    private val text = "Государство — это организация политической власти. Государство осуществляет управление " +
        "обществом и обеспечивает его безопасность. Теория насилия разрабатывалась Е. Дюрингом (1833—1921), " +
        "К. Каутским и др. Государство обладает органами пуб личной власти."

    @Test
    fun exactQuoteIgnoringCaseAndPunctuation() {
        val r = QuoteMatcher.find(text, "государство это организация политической власти")
        assertNotNull(r)
        assertEquals("Государство — это организация политической власти", text.substring(r!!))
    }

    @Test
    fun brokenWordInPdfStillMatches() {
        assertNotNull(QuoteMatcher.find(text, "Государство обладает органами публичной власти."))
    }

    @Test
    fun slightlyChangedQuoteMatches() {
        val r = QuoteMatcher.find(text, "Теория насилия разрабатывалась Дюрингом, Каутским и другими")
        assertNotNull(r)
    }

    @Test
    fun inventedQuoteIsRejected() {
        assertNull(QuoteMatcher.find(text, "Государство возникло по воле Бога и установлено свыше навечно"))
        assertNull(QuoteMatcher.find(text, "власти"))
    }
}
