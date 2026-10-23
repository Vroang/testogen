package com.testogen.app.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сверка предпросмотра с файлом: для всех сочетаний раскладки, ориентации, колонок и шрифта
 * на маленьком, среднем и большом тесте пишет .docx и ожидаемые положения блоков (по модели Paginator)
 * в build/layout-check. Сравнение с LibreOffice — `python tools/layout_check.py app/build/layout-check`.
 */
class LayoutCheckTest {

    private val long = listOf(
        "Какая теория происхождения государства рассматривала государство как аппарат принуждения, возникающий в результате развития экономических отношений и складывания двух больших социальных групп?" to
            listOf("классовая теория", "теологическая теория", "патриархальная теория", "теория общественного договора"),
        "Как называется форма правления, при которой верховная государственная власть частично или полностью принадлежит одному лицу и, как правило, передаётся по наследству?" to
            listOf("республика", "демократия", "монархия", "федерация"),
        "Что относится к дисциплинарным взысканиям?" to
            listOf("предупреждение, дисквалификация, штраф", "возмещение убытков, неустойка, компенсация морального вреда", "замечание, выговор, увольнение", "лишение свободы, арест, ограничение свободы"),
        "Какой документ был утверждён как морской торговый флаг России?" to
            listOf("чёрно-жёлто-белый флаг", "Государственное знамя жёлтого цвета", "бело-сине-красный флаг", "красное полотнище с золотой звездой"),
        "Как называется процесс устойчивого общего роста цен на товары и услуги?" to
            listOf("эмиссия", "дефляция", "инфляция", "деноминация"),
        "Какое событие связано с появлением современного российского флага как государственного?" to
            listOf(
                "он был утверждён Александром III вместо прежнего флага",
                "он был утверждён Александром II как чёрно-жёлто-белый",
                "он был изготовлен к восхождению на престол Елизаветы Петровны",
                "он стал личным штандартом Петра Великого в 1693 г.",
            ),
        "Каким образом был избран первый представитель царской династии Романовых Михаил Фёдорович?" to
            listOf("Сенатом", "Земским собором", "парламентом", "Боярской думой"),
    )

    private fun variants(count: Int, perVariant: Int) = List(count) { v ->
        DocVariant(v + 1, List(perVariant) { i -> long[(v * 3 + i) % long.size].let { (q, o) -> DocQuestion(i + 1, q, o, (v + i) % 4) } })
    }

    @Test
    fun writeAllCombinations() {
        val out = File("build/layout-check").apply { deleteRecursively(); mkdirs() }
        val p = Paginator(AwtMeasurer())
        val sets = mapOf("small" to variants(4, 5), "medium" to variants(5, 14), "big" to variants(4, 30), "many" to variants(40, 30))
        var count = 0
        for ((name, vs) in sets) for (answers in AnswersPlacement.entries) for (mode in LayoutMode.entries) for (landscape in listOf(false, true)) for (columns in 1..2)
            for (font in if (answers == AnswersPlacement.SEPARATE) listOf(10f, 11f, 12f, 13f, 14f) else listOf(10f, 13f)) {
            if (name == "many" && answers == AnswersPlacement.SEPARATE) continue
            val l = DocLayout(mode, landscape, columns, font, cutLines = true, auto = false, answers = answers)
            val a = when (answers) { AnswersPlacement.SEPARATE -> ""; AnswersPlacement.END -> "-ansend"; AnswersPlacement.NEW_PAGE -> "-anspage" }
            val id = "$name-${mode.name.lowercase()}-${if (landscape) "land" else "port"}-${columns}col-${font.toInt()}$a"
            File(out, "$id.docx").writeBytes(DocxBuilder.test(id, vs, l, p))
            File(out, "$id.json").writeText(layoutJson(p.paginate(vs, l), l))
            count++
        }
        assertTrue(count > 0)
    }
}
