package com.testogen.app.textbook

import org.junit.Assert.assertEquals
import org.junit.Test

class TextbookTitleTest {
    @Test
    fun fileNameKeepsCyrillic() {
        assertEquals("Обществознание 9 класс", TextbookImporter.titleFromFileName("Обществознание_9_класс.pdf"))
        assertEquals("Obshchestvoznanie 9", TextbookImporter.titleFromFileName("Obshchestvoznanie_9.pdf"))
    }

    @Test
    fun titleSourcesInOrder() {
        assertEquals("Обществознание. 9 класс", TextbookImporter.chooseTitle(null, "Обществознание. 9 класс", "Obshchestvoznanie 9"))
        assertEquals("Обществознание. 9 класс", TextbookImporter.chooseTitle("", "Обществознание. 9 класс", "Obshchestvoznanie 9"))
        // Мусор в свойствах PDF не берётся
        assertEquals("Обществознание. 9 класс", TextbookImporter.chooseTitle("obsh9_2026.indd", "Обществознание. 9 класс", "x"))
        assertEquals("История России. 8 класс", TextbookImporter.chooseTitle("История России. 8 класс", "История", "x"))
        assertEquals("Учебник истории", TextbookImporter.chooseTitle(null, null, "Учебник истории"))
    }
}
