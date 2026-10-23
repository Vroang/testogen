package com.testogen.app.data

import androidx.room.withTransaction
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.TestEntity
import com.testogen.app.data.db.TestItemEntity

/** Тесты (черновики) в базе: создание, удаление и возврат вопроса в варианте. */
class TestRepository(private val db: AppDatabase) {
    private val dao = db.tests()

    suspend fun create(
        title: String,
        topics: List<String>,
        difficulties: List<Int>,
        allowRepeats: Boolean,
        variants: List<List<Long>>,
        perVariant: Int,
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            TestEntity(
                title = title,
                topics = topics.joinToString("\n"),
                variantsCount = variants.size,
                questionsPerVariant = perVariant,
                difficulties = difficulties.joinToString(","),
                allowRepeats = allowRepeats,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.insertItems(
            variants.flatMapIndexed { v, ids -> ids.mapIndexed { p, q -> TestItemEntity(id, v, p, q) } },
        )
        id
    }

    /** Убрать вопрос из варианта; номера следующих сдвигаются. Возвращает удалённое — для «Вернуть». */
    suspend fun removeItem(item: TestItemEntity) = db.withTransaction {
        dao.deleteItem(item.testId, item.variant, item.position)
        dao.shiftNegative(item.testId, item.variant, item.position + 1, -1)
        dao.flipPositive(item.testId, item.variant)
        dao.touch(item.testId)
    }

    suspend fun restoreItem(item: TestItemEntity) = db.withTransaction {
        dao.shiftNegative(item.testId, item.variant, item.position, 1)
        dao.flipPositive(item.testId, item.variant)
        dao.insertItems(listOf(item))
        dao.touch(item.testId)
    }

    suspend fun replace(item: TestItemEntity, questionId: Long) {
        dao.setQuestion(item.testId, item.variant, item.position, questionId)
        dao.touch(item.testId)
    }
}
