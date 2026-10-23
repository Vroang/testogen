package com.testogen.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TestBuilderTest {

    private fun pool(n: Int, topics: Int = 1) = List(n) { Candidate(it.toLong(), it % 3 + 1, "t${it % topics}") }

    @Test
    fun noRepeatsBetweenVariantsAndBalancedDifficulty() {
        val r = TestBuilder.build(pool(90, topics = 2), variants = 4, perVariant = 15, allowRepeats = false, random = Random(1))
        val variants = (r as TestBuilder.Result.Ok).variants
        assertEquals(4, variants.size)
        variants.forEach { assertEquals(15, it.size) }
        val all = variants.flatten()
        assertEquals(all.size, all.toSet().size)
        // В каждом варианте каждой сложности по 5 (в банке поровну)
        variants.forEach { v -> assertEquals(listOf(5, 5, 5), v.groupingBy { it % 3 }.eachCount().values.sorted()) }
    }

    @Test
    fun notEnoughWithoutRepeats() {
        val r = TestBuilder.build(pool(30), variants = 4, perVariant = 10, allowRepeats = false)
        assertEquals(TestBuilder.Result.NotEnough(30, 40), r)
    }

    @Test
    fun repeatsSpreadEvenlyAndNoneInsideVariant() {
        val r = TestBuilder.build(pool(25), variants = 6, perVariant = 10, allowRepeats = true, random = Random(2)) as TestBuilder.Result.Ok
        r.variants.forEach { assertEquals(it.size, it.toSet().size) }
        val uses = r.variants.flatten().groupingBy { it }.eachCount().values
        assertTrue(uses.max() - uses.min() <= 1)
    }

    @Test
    fun hugeTestIsFast() {
        val start = System.nanoTime()
        val r = TestBuilder.build(pool(2000, topics = 5), variants = 40, perVariant = 40, allowRepeats = false) as TestBuilder.Result.Ok
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1600, r.variants.flatten().toSet().size)
        assertTrue("$ms мс", ms < 300)
    }
}
