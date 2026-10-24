package com.testogen.app.ai

import com.testogen.app.data.db.Difficulty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** Вопрос, как его вернула нейросеть: верный ответ отдельно, три неверных отдельно. */
data class ParsedQuestion(
    val question: String,
    val correct: String,
    val distractors: List<String>,
    val difficulty: Difficulty,
    val explanation: String,
    val quote: String,
)

/** Вопрос с перемешанными вариантами, готовый к сохранению. */
data class ShuffledQuestion(val source: ParsedQuestion, val options: List<String>, val correctIndex: Int)

/** Разбор ответа нейросети, проверки и отсев похожих вопросов. Без Android — проверяется юнит-тестами. */
object QuestionQuality {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Отбракованный вопрос и причина (для журнала и отладки). */
    data class Rejected(val text: String, val reason: String)

    data class ParseResult(val accepted: List<ParsedQuestion>, val rejected: List<Rejected>)

    /** [allowLatin] — тема про иностранный язык: латинские слова в вопросах допустимы. */
    fun parse(content: String, allowLatin: Boolean = false): ParseResult {
        val root = parseObject(content) ?: return ParseResult(emptyList(), listOf(Rejected(content.take(80), "не JSON")))
        val items = root["questions"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return ParseResult(emptyList(), emptyList())
        val accepted = mutableListOf<ParsedQuestion>()
        val rejected = mutableListOf<Rejected>()
        for (item in items) {
            val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
            val q = ParsedQuestion(
                question = stripSourceRefs(obj.str("question").cleanText()),
                correct = obj.str("correct").cleanOption(),
                distractors = obj["distractors"]?.let { runCatching { it.jsonArray }.getOrNull() }
                    ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()?.cleanOption() }
                    .orEmpty(),
                difficulty = when (obj.str("difficulty").trim().lowercase()) {
                    "easy" -> Difficulty.EASY
                    "hard" -> Difficulty.HARD
                    else -> Difficulty.MEDIUM
                },
                explanation = obj.str("explanation").cleanText(),
                quote = obj.str("quote").trim(),
            )
            val problem = problem(q, allowLatin)
            if (problem == null) accepted += q else rejected += Rejected(q.question.ifBlank { "(пусто)" }, problem)
        }
        return ParseResult(accepted, rejected)
    }

    /** Причина отбраковки или null, если вопрос годится. */
    fun problem(q: ParsedQuestion, allowLatin: Boolean = false): String? {
        if (q.question.length < 10) return "слишком короткий вопрос"
        if (q.question.length > 500) return "слишком длинный вопрос"
        // Оборот, который не вычищается без переписывания фразы («учебник относит…»): ученику на бумаге он непонятен
        if (TEXTBOOK_WORD.containsMatchIn(q.question)) return "вопрос ссылается на учебник"
        // «Какая черта characterizes …»: английское слово посреди русского текста — вопрос отбрасывается и добирается заново
        if (!allowLatin && (listOf(q.question, q.correct) + q.distractors).any { hasLatinWord(it) }) return "латинские слова в русском тексте"
        if (q.correct.isBlank()) return "нет верного ответа"
        if (q.distractors.size != 3) return "неверных вариантов не три"
        val all = listOf(q.correct) + q.distractors
        if (all.any { it.isBlank() }) return "пустой вариант"
        if (all.any { it.length > 200 }) return "слишком длинный вариант"
        if (all.map { normalize(it) }.toSet().size != 4) return "варианты повторяются"
        if (all.any { BANNED_OPTION.containsMatchIn(it) }) return "вариант «все/ничего из перечисленного»"
        val longestWrong = q.distractors.maxOf { it.length }
        if (q.correct.length > longestWrong * 3 / 2 && q.correct.length - longestWrong > 15) return "верный ответ заметно длиннее остальных"
        val correctNorm = normalize(q.correct)
        if (correctNorm.length >= 6 && normalize(q.question).contains(correctNorm)) return "ответ подсказан в вопросе"
        return null
    }

    /**
     * Латинское слово из трёх и больше букв, где есть строчные: «characterizes», «Google». Не считаются:
     * аббревиатуры и римские цифры (только заглавные — NATO, XIX), обозначения из одной-двух букв (x, pH),
     * формулы и слова с цифрами (H2O, mp3).
     */
    private val LATIN_WORD = Regex("""(?<![\p{L}\d])[A-Za-z]*[a-z][A-Za-z]*(?![\p{L}\d])""")

    fun hasLatinWord(text: String) = LATIN_WORD.findAll(text).any { it.value.length >= 3 }

    /** Тема про иностранный язык — там латиница в вопросах нормальна. */
    fun foreignLanguageTopic(topic: String) = FOREIGN_TOPIC.containsMatchIn(topic)

    private val FOREIGN_TOPIC = Regex(
        """(?iu)английск|немецк|французск|испанск|итальянск|латинск|латын|иностранн|english|german|french|spanish|deutsch""",
    )

    private val TEXTBOOK_WORD = Regex("""(?iu)(?<!\p{L})(?:учебник|параграф)\p{L}*""")

    /** На что ссылается оборот: текст, учебник, параграф, автор. */
    private const val SOURCE = """(?:(?:текст|учебник|параграф)[аеу]?|автор(?:а|у|ов|ы|ам)?)"""

    /** Слово-сказуемое после оборота («в тексте назван», «согласно тексту является»): по окончанию глагола или краткого причастия. */
    private const val VERB =
        """\p{L}+(?:ется|ются|ится|ятся|ет|ит|ут|ют|ат|ят|ал|ала|ало|али|ался|алась|алось|ались|ил|ила|ило|или|ел|ела|ело|ели|ан|ана|ано|аны|ен|ена|ено|ены|ён|ёна|ёно|ёны)(?!\p{L})"""

    /**
     * «согласно тексту (учебника)», «по тексту», «по данным учебника», «по мнению автора», «как сказано в параграфе»,
     * «в тексте (учебника)», «из текста» — вместе с запятыми вокруг. Без уточнения «учебника/параграфа/выше» оборот
     * убирается, только если за ним запятая, знак конца или сказуемое: «в тексте Конституции», «согласно тексту закона»,
     * «по мнению автора теории» не трогаются.
     */
    private val SOURCE_REF = Regex(
        """(?iu)(?:,\s*)?(?<!\p{L})(?:согласно|по|судя\s+по|исходя\s+из|в\s+соответствии\s+с|по\s+данным|по\s+мнению|""" +
            """как\s+(?:сказано|указано|говорится|отмечается|описано|написано|утверждается)\s+в|в|из)\s+$SOURCE""" +
            """(?:\s+(?:учебника|параграфа|выше)|(?=\s*[,?.!:;]|\s+$VERB))(?:\s*,)?(?=[\s?.!,:;]|$)""",
    )

    /** «Какое утверждение … соответствует тексту учебника?» → «Какое утверждение … верно?» */
    private val MATCHES_TEXT = Regex(
        """(?iu)(?<!\p{L})(?:соответствует|согласуется\s+с|не\s+противоречит)\s+(?:(?:тексту|текстом|содержанию|содержанием)(?:\s+(?:учебника|параграфа))?|учебнику|учебником|параграфу|параграфом)(?!\p{L})""",
    )

    /** Убирает «согласно тексту учебника», «в тексте» — для школьника в бумажном тесте они лишние. */
    fun stripSourceRefs(q: String): String {
        val s = q.replace(MATCHES_TEXT, "верно").replace(SOURCE_REF, "")
            .replace(Regex("""\s+([?,.!:;])"""), "$1").replace(Regex("""\s{2,}"""), " ").trim()
            .removePrefix(",").trim()
        return s.replaceFirstChar { it.uppercaseChar() }
    }

    /** Перемешивает варианты: позиция верного ответа случайна. */
    fun shuffle(q: ParsedQuestion, random: Random = Random.Default): ShuffledQuestion {
        val options = (listOf(q.correct) + q.distractors).shuffled(random)
        return ShuffledQuestion(q, options, options.indexOf(q.correct))
    }

    // ---------- Похожесть ----------

    /** Ключ для поиска точных дублей: нижний регистр, ё→е, только буквы и цифры. */
    fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() || it == ' ' }
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** Значимые «основы» слов: без коротких служебных слов, обрезаны до 5 букв (грубая замена стемминга). */
    fun stems(text: String): Set<String> =
        normalize(text).split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .map { if (it.length > 5 && it.any(Char::isLetter)) it.take(5) else it }
            .toSet()

    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { it in b }
        return common.toDouble() / (a.size + b.size - common)
    }

    /** Отпечаток вопроса для сравнения: основы текста вопроса и верного ответа. */
    class Fingerprint(val stems: Set<String>, val answer: String)

    fun fingerprint(question: String, answer: String) = Fingerprint(
        stems(question),
        normalize(answer).split(' ').filter { it !in ANSWER_NOISE }.joinToString(" "),
    )

    /** Доля общих основ от меньшего набора. */
    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.count { it in b }.toDouble() / minOf(a.size, b.size)
    }

    /**
     * Похожие вопросы в одной теме. Тот же верный ответ — повтор: нейросеть охотно спрашивает
     * про один и тот же факт разными словами («Табель о рангах» трижды). Разные ответы —
     * повтор, только если текст почти совпадает: «Когда была битва X?» и «Кто командовал в битве X?»
     * — разные вопросы.
     */
    fun isDuplicate(a: Fingerprint, b: Fingerprint): Boolean =
        if (a.answer.length >= 3 && a.answer == b.answer) {
            true
        } else {
            similarity(a.stems, b.stems) >= 0.75
        }

    // ---------- Вспомогательное ----------

    private fun parseObject(content: String): JsonObject? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return runCatching { json.parseToJsonElement(content.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    private fun JsonObject.str(name: String): String =
        this[name]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    private fun String.cleanText(): String = trim().replace(Regex("\\s+"), " ")

    /** Убирает нумерацию, которую модель иногда ставит сама: «А) », «1. », «- » (но не инициалы «В. И.»). */
    private fun String.cleanOption(): String =
        cleanText().replace(Regex("^([А-ГA-Dа-гa-d]\\)|[1-4][).]|[-–—•])\\s+"), "").trimEnd(';').trim()

    private val BANNED_OPTION = Regex("(?iu)(все|всё|ничего|ни один|оба)\\s+(из\\s+)?(перечисленн|вышеперечисленн|ответ|вариант)")

    private val ANSWER_NOISE = setOf("г", "гг", "год", "году", "года", "в", "во", "веке", "век", "до", "н", "э")

    private val STOP_WORDS = setOf(
        "как", "что", "кто", "это", "или", "при", "для", "его", "она", "они", "был", "была", "было",
        "были", "так", "чем", "под", "над", "без", "где", "когда", "какой", "какая", "какое", "какие",
        "каком", "какого", "каких", "которые", "который", "которая", "году", "веке", "век",
        "один", "одна", "одно", "назывался", "называется", "называют", "является", "являлся",
    )
}
