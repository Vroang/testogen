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
}
