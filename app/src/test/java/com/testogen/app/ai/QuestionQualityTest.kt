package com.testogen.app.ai

import com.testogen.app.data.db.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionQualityTest {

    private fun q(
        question: String = "В каком году произошло Ледовое побоище?",
        correct: String = "1242",
        distractors: List<String> = listOf("1240", "1380", "1223"),
    ) = ParsedQuestion(question, correct, distractors, Difficulty.EASY, "", "")

    @Test
    fun parsesValidJsonAndKeepsModelDifficulty() {
        val content = """```json
            {"questions":[{"question":"Кто возглавил русское войско на Чудском озере?","correct":"Александр Невский",
            "distractors":["Дмитрий Донской","Ярослав Мудрый","Иван Калита"],"difficulty":"hard","explanation":"Так.","quote":""}]}
            ```"""
        val result = QuestionQuality.parse(content)
        assertEquals(1, result.accepted.size)
        assertEquals(Difficulty.HARD, result.accepted[0].difficulty)
        assertEquals("Александр Невский", result.accepted[0].correct)
    }

    @Test
    fun rejectsBrokenQuestions() {
        assertNotNull(QuestionQuality.problem(q(distractors = listOf("1240", "1380"))))
        assertNotNull(QuestionQuality.problem(q(distractors = listOf("1240", "1242", "1223"))))
        assertNotNull(QuestionQuality.problem(q(distractors = listOf("1240", "Все перечисленные ответы", "1223"))))
        assertNotNull(QuestionQuality.problem(q(question = "Где правил Александр Невский, князь новгородский?", correct = "Александр Невский")))
        // Ссылку «учебник относит…» не вычистить без переписывания — такой вопрос отбрасывается
        assertNotNull(QuestionQuality.problem(q(question = "Какие потребности человека учебник относит к духовным?")))
        assertNotNull(
            QuestionQuality.problem(
                q(
                    question = "Какое мероприятие Петра I изменило быт дворянства?",
                    correct = "Обязательное обучение дворян и введение европейской одежды",
                    distractors = listOf("Введение подушной подати", "Создание регулярных полков", "Учреждение Сената и коллегий"),
                ),
            ),
        )
        assertEquals(null, QuestionQuality.problem(q()))
    }

    @Test
    fun parseStripsNumberingButKeepsInitials() {
        val content = """{"questions":[{"question":"Кто был первым председателем Совнаркома?","correct":"В. И. Ленин",
            "distractors":["Б) Л. Д. Троцкий","1. Я. М. Свердлов","- И. В. Сталин"],"difficulty":"medium"}]}"""
        val accepted = QuestionQuality.parse(content).accepted.single()
        assertEquals("В. И. Ленин", accepted.correct)
        assertEquals(listOf("Л. Д. Троцкий", "Я. М. Свердлов", "И. В. Сталин"), accepted.distractors)
    }

    @Test
    fun removesReferencesToTextbookText() {
        assertEquals("Что является общепризнанным эквивалентом?", QuestionQuality.stripSourceRefs("Что, согласно тексту, является общепризнанным эквивалентом?"))
        assertEquals("Какой вид ответственности назван самым суровым?", QuestionQuality.stripSourceRefs("Какой вид ответственности в тексте назван самым суровым?"))
        assertEquals("Кто сформулировал патриархальную теорию?", QuestionQuality.stripSourceRefs("Кто, согласно тексту учебника, сформулировал патриархальную теорию?"))
        assertEquals("Как называется налог?", QuestionQuality.stripSourceRefs("Как называется налог?"))
        // Обороты со скриншотов теста
        val cases = mapOf(
            "Какая теория происхождения государства, по тексту учебника, рассматривала государство как аппарат принуждения?" to
                "Какая теория происхождения государства рассматривала государство как аппарат принуждения?",
            "Как в тексте учебника называется форма правления?" to "Как называется форма правления?",
            "Какое событие, по тексту учебника, связано с появлением флага?" to "Какое событие связано с появлением флага?",
            "Что, согласно тексту учебника, лежит в основе суверенитета государства?" to "Что лежит в основе суверенитета государства?",
            "Какой документ, согласно тексту учебника, был утверждён как морской торговый флаг России?" to
                "Какой документ был утверждён как морской торговый флаг России?",
            "Согласно учебнику, что является формой правления?" to "Что является формой правления?",
            "Что такое инфляция, согласно тексту?" to "Что такое инфляция?",
            "Какой принцип, по данным учебника, лежит в основе права?" to "Какой принцип лежит в основе права?",
            "Как сказано в параграфе, что относится к функциям государства?" to "Что относится к функциям государства?",
            "Что, по мнению автора, отличает гражданина?" to "Что отличает гражданина?",
            "Что из текста учебника следует о суверенитете?" to "Что следует о суверенитете?",
            "Какое утверждение о признаках унитарного государства соответствует тексту учебника?" to
                "Какое утверждение о признаках унитарного государства верно?",
            "Какое из утверждений согласуется с содержанием параграфа?" to "Какое из утверждений верно?",
            "Какое суждение не противоречит тексту?" to "Какое суждение верно?",
            "Какое положение о соотношении природного и социального в человеке соответствует учебнику?" to
                "Какое положение о соотношении природного и социального в человеке верно?",
        )
        cases.forEach { (raw, clean) -> assertEquals(clean, QuestionQuality.stripSourceRefs(raw)) }
        // Не ссылки на учебник — не трогаются
        listOf(
            "Что закреплено в тексте Конституции Российской Федерации?",
            "Какой принцип, согласно тексту закона, применяется к сделкам?",
            "Какое событие описано в тексте летописи?",
            "Кто, по мнению автора теории общественного договора, является источником власти?",
            "Кто является автором теории общественного договора?",
            "Какие учебники вышли в 1990-е годы?",
        ).forEach { assertEquals(it, QuestionQuality.stripSourceRefs(it)) }
    }

    @Test
    fun shuffleKeepsCorrectAnswerAndMovesIt() {
        val positions = (0 until 200).map { QuestionQuality.shuffle(q(), Random(it)) }
        positions.forEach { assertEquals("1242", it.options[it.correctIndex]) }
        assertEquals(4, positions.map { it.correctIndex }.toSet().size)
    }

    @Test
    fun detectsParaphrasedDuplicates() {
        val a = QuestionQuality.fingerprint("В каком году состоялось Ледовое побоище?", "1242")
        val b = QuestionQuality.fingerprint("Ледовое побоище произошло в каком году?", "1242 г")
        val c = QuestionQuality.fingerprint("Кто командовал русскими войсками в Куликовской битве?", "Дмитрий Донской")
        val d = QuestionQuality.fingerprint("Кто командовал русскими войсками в Ледовом побоище?", "Александр Невский")
        assertTrue(QuestionQuality.isDuplicate(a, b))
        assertFalse(QuestionQuality.isDuplicate(a, c))
        assertFalse(QuestionQuality.isDuplicate(a, d))
        val e = QuestionQuality.fingerprint("Какой документ определял порядок продвижения по службе?", "Табель о рангах")
        val f = QuestionQuality.fingerprint("Что закрепило назначение на должности по личным заслугам?", "Табель о рангах")
        assertTrue(QuestionQuality.isDuplicate(e, f))
    }

    @Test
    fun splitsCountsBetweenDifficulties() {
        val all = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
        assertEquals(mapOf(Difficulty.EASY to 4, Difficulty.MEDIUM to 4, Difficulty.HARD to 4), QuestionGenerator.split(12, all))
        assertEquals(mapOf(Difficulty.EASY to 4, Difficulty.MEDIUM to 4, Difficulty.HARD to 3), QuestionGenerator.split(11, all))
        assertEquals(mapOf(Difficulty.HARD to 7), QuestionGenerator.split(7, listOf(Difficulty.HARD)))
        // Лёгких уже хватает — добираем средние и сложные
        val ask = QuestionGenerator.askCounts(13, mapOf(Difficulty.EASY to 0, Difficulty.MEDIUM to 2, Difficulty.HARD to 6))
        assertEquals(13, ask.values.sum())
        assertEquals(null, ask[Difficulty.EASY])
        assertTrue(ask.getValue(Difficulty.HARD) > ask.getValue(Difficulty.MEDIUM))
    }

    @Test
    fun costOfTwentyQuestionsIsAboutACent() {
        val cost = QuestionGenerator.estimateCostUsd(20, review = true, existingInTopic = 0, rules = 0)
        assertTrue("cost=$cost", cost in 0.002..0.03)
    }

    @Test
    fun latinWordsInRussianQuestionAreRejected() {
        assertNotNull(QuestionQuality.problem(q(question = "Какая черта characterizes демократический режим?")))
        assertNotNull(QuestionQuality.problem(q(question = "Какой режим называют демократическим?", correct = "power of people")))
        // Аббревиатуры, римские цифры, формулы, одна-две буквы — можно
        listOf(
            "Когда была создана организация NATO?",
            "Какое событие произошло в XIX веке?",
            "Какая формула у воды — H2O?",
            "Что показывает уровень pH раствора?",
        ).forEach { assertNull(it, QuestionQuality.problem(q(question = it))) }
        // Тема — иностранный язык
        assertTrue(QuestionQuality.foreignLanguageTopic("Английский язык, 7 класс"))
        assertNull(QuestionQuality.problem(q(question = "Как переводится слово characterizes?"), allowLatin = true))
    }
}
