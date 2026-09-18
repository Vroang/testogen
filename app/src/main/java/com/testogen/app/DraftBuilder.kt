package com.testogen.app

/**
 * Черновик теста, собранный на главном экране. Хранится в памяти процесса:
 * экран черновика читает его при открытии.
 */
object DraftHolder {
    var variants: List<List<Question>> = emptyList()
    var questionsPerVariant: Int = 0
}

/**
 * Сборка черновика: фильтрация банка и распределение вопросов по вариантам.
 */
object DraftBuilder {

    fun filterQuestions(
        all: List<Question>,
        difficulties: Collection<String>,
        trickyEnabled: Boolean,
        topicFilter: String = ""
    ): List<Question> = all.filter { question ->
        question.difficulty in difficulties &&
            (trickyEnabled || !question.tricky) &&
            (topicFilter.isBlank() || question.topic.equals(topicFilter, ignoreCase = true))
    }

    fun build(
        filtered: List<Question>,
        variantsCount: Int,
        questionsPerVariant: Int
    ): List<List<Question>> {
        if (filtered.isEmpty() || variantsCount <= 0 || questionsPerVariant <= 0) {
            return List(variantsCount.coerceAtLeast(0)) { emptyList() }
        }
        val pool = filtered.shuffled()
        val n = pool.size
        val result = ArrayList<List<Question>>(variantsCount)
        var cursor = 0
        repeat(variantsCount) {
            val items = ArrayList<Question>(questionsPerVariant)
            while (items.size < questionsPerVariant) {
                if (cursor >= n) cursor = 0
                while (items.size < questionsPerVariant && cursor < n) {
                    items.add(pool[cursor])
                    cursor++
                }
                if (items.size < questionsPerVariant) {
                    // Банк закончился: добираем с начала, по возможности
                    // не повторяясь внутри одного варианта.
                    val remaining = questionsPerVariant - items.size
                    val notInVariant = pool.filter { candidate ->
                        items.none { it.id == candidate.id }
                    }
                    val fill = if (notInVariant.isNotEmpty()) {
                        notInVariant.shuffled().take(remaining)
                    } else {
                        pool.shuffled().take(remaining)
                    }
                    items.addAll(fill)
                }
            }
            // Порядок внутри варианта не совпадает с порядком банка.
            result.add(items.shuffled())
        }
        return result
    }
}
