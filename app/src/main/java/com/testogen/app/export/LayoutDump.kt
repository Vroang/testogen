package com.testogen.app.export

/**
 * Где по модели Paginator стоит каждый блок (страница, левый край, верх первой строки текста) — в JSON
 * для сверки с LibreOffice (`tools/layout_check.py`). Пишется тестом LayoutCheckTest и debug-сборкой при «Отправить».
 */
fun layoutJson(pages: List<Paginator.Page>, l: DocLayout): String = buildString {
    append("{\"pages\":${pages.size},\"pageHeight\":${l.pageHeight},\"lineHeight\":${l.lineHeight},\"blocks\":[")
    var first = true
    fun add(page: Int, kind: String, x: Float, width: Float, top: Float, bottom: Float, text: String) {
        if (!first) append(',')
        first = false
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        append("{\"page\":$page,\"kind\":\"$kind\",\"x\":$x,\"width\":$width,\"top\":$top,\"bottom\":$bottom,\"text\":\"$escaped\"}")
    }
    pages.forEachIndexed { i, page ->
        // Таблицы ответов: «Ответы», шапка и первый и последний вариант каждого куска (ищутся по колонке «Вар.»)
        for (t in page.answers) {
            var k = 0
            if (t.title) add(i + 1, "cell", t.x, t.width, t.rowTop(k), t.rowTop(k++) + t.lineHeight, "Ответы")
            add(i + 1, "cell", t.x, t.firstWidth, t.rowTop(k), t.rowTop(k) + t.lineHeight, DocxBuilder.ANSWER_HEADER)
            add(i + 1, "cell", t.x, t.firstWidth, t.rowTop(k + 1), t.rowTop(k + 1) + t.lineHeight, "${t.rows.first().number}")
            val last = k + t.rows.size
            if (t.rows.size > 1) add(i + 1, "cell", t.x, t.firstWidth, t.rowTop(last), t.rowTop(last) + t.lineHeight, "${t.rows.last().number}")
        }
        for (b in page.blocks) {
            val (text, top) = when (b) {
                is Paginator.PlacedTitle -> Paginator.titleText(b.variant) to b.y
                is Paginator.PlacedQuestion -> "${b.question.number}. ${b.question.text}" to b.y + if (b.atTop) 0f else Paginator.spacingBeforeQuestion(l)
            }
            add(i + 1, if (b is Paginator.PlacedTitle) "title" else "question", b.x, b.width, top, b.y + b.height, text)
        }
    }
    append("]}")
}
