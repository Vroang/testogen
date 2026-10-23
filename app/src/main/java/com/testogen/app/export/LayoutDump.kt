package com.testogen.app.export

/**
 * Где по модели Paginator стоит каждый блок (страница, левый край, верх первой строки текста) — в JSON
 * для сверки с LibreOffice (`tools/layout_check.py`). Пишется тестом LayoutCheckTest и debug-сборкой при «Отправить».
 */
fun layoutJson(pages: List<Paginator.Page>, l: DocLayout): String = buildString {
    append("{\"pages\":${pages.size},\"pageHeight\":${l.pageHeight},\"lineHeight\":${l.lineHeight},\"blocks\":[")
    var first = true
    pages.forEachIndexed { i, page ->
        for (b in page.blocks) {
            val (text, top) = when (b) {
                is Paginator.PlacedTitle -> Paginator.titleText(b.variant, b.continued) to b.y
                is Paginator.PlacedQuestion -> "${b.question.number}. ${b.question.text}" to b.y + if (b.atTop) 0f else Paginator.spacingBeforeQuestion(l)
            }
            if (!first) append(',')
            first = false
            val kind = if (b is Paginator.PlacedTitle) "title" else "question"
            val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
            append("{\"page\":${i + 1},\"kind\":\"$kind\",\"x\":${b.x},\"width\":${b.width},\"top\":$top,\"bottom\":${b.y + b.height},\"text\":\"$escaped\"}")
        }
    }
    append("]}")
}
