package com.testogen.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

class DocxExportTest {

    private val sample = listOf(
        "Кто является автором теории общественного договора, изложенной в труде «Об общественном договоре»?" to
            listOf("К. Маркс", "Фома Аквинский", "Ж.-Ж. Руссо", "Аристотель"),
        "Что лежит в основе суверенитета государства?" to
            listOf("разделение властей на три ветви", "самодостаточность в ключевых областях", "наличие парламента и судебной системы", "всеобщее избирательное право граждан"),
        "Какая функция государства определяет участие государства в регулировании экономики, например через сбор налогов и организацию денежного обращения?" to
            listOf("экономическая", "социальная", "экологическая", "политическая"),
        "Что понимается под функциями государства?" to
            listOf(
                "административная и территориальная организация порядка осуществления государственной власти",
                "основные направления его деятельности для решения стоящих перед обществом проблем и задач",
                "совокупность методов осуществления власти и способов взаимоотношения органов власти и граждан",
                "организация механизма осуществления верховной власти и формирования высших органов",
            ),
        "Какая характеристика отличает тоталитарный режим от авторитарного?" to
            listOf("всеобъемлющий контроль всех сторон жизни граждан", "концентрация власти в руках одного субъекта", "провозглашение прав и свобод без их реализации", "выборность представительных органов"),
    )

    private fun variants(count: Int, perVariant: Int) = List(count) { v ->
        DocVariant(v + 1, List(perVariant) { i -> sample[(v + i) % sample.size].let { (q, o) -> DocQuestion(i + 1, q, o, (v + i) % 4) } })
    }

    private val out = File("build/docx-check").apply { mkdirs() }

    @Test
    fun smallTestIsCompactLikeSample() {
        val p = Paginator(AwtMeasurer())
        val vs = variants(4, 5)
        val layout = p.auto(vs)
        assertEquals(LayoutMode.COMPACT, layout.mode)
        assertTrue(layout.landscape)
        val pages = p.paginate(vs, layout)
        File(out, "small.docx").writeBytes(DocxBuilder.test("Государство", vs, layout, p))
        File(out, "small.txt").writeText("pages=${pages.size} layout=$layout")
        File(out, "answers.docx").writeBytes(DocxBuilder.answers("Государство", variants(12, 25)))
        assertValidZip(File(out, "small.docx"))
    }

    @Test
    fun bigTestIsVariantPerPage() {
        val p = Paginator(AwtMeasurer())
        val vs = variants(3, 30)
        val layout = p.auto(vs)
        assertEquals(LayoutMode.PER_PAGE, layout.mode)
        val pages = p.paginate(vs, layout)
        File(out, "big.docx").writeBytes(DocxBuilder.test("Государство", vs, layout, p))
        File(out, "big.txt").writeText("pages=${pages.size} layout=$layout\n" + pages.mapIndexed { i, pg -> "page ${i + 1}: " + pg.blocks.joinToString { b -> if (b is Paginator.PlacedTitle) "T${b.variant}" else "Q${(b as Paginator.PlacedQuestion).question.number}" } }.joinToString("\n"))
    }

    private fun assertValidZip(f: File) {
        val names = mutableListOf<String>()
        ZipInputStream(f.inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "word/document.xml", "word/styles.xml")))
    }
}
