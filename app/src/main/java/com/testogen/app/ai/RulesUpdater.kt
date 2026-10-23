package com.testogen.app.ai

import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.AiRuleEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Превращает новые причины замен в правила «Чего не делать».
 * Правила только ДОПОЛНЯЮТСЯ: старые не трогаются, новые похожие на старые отбрасываются.
 */
class RulesUpdater(private val db: AppDatabase, private val ai: AiClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Возвращает число добавленных правил. */
    suspend fun learnFromReasons(): Int {
        val reasons = db.replacementReasons().unprocessed()
        if (reasons.isEmpty()) return 0
        val current = db.aiRules().all()
        val result = ai.complete(
            ChatRequest(
                operation = "rules",
                system = Prompts.RULES_SYSTEM,
                user = Prompts.rulesUser(current.map { it.text }, reasons.map { it.questionText to it.reason }),
                maxTokens = 800,
                temperature = 0.3,
            ),
        )
        val proposed = runCatching {
            val c = result.content
            json.parseToJsonElement(c.substring(c.indexOf('{'), c.lastIndexOf('}') + 1))
                .jsonObject["rules"]!!.jsonArray
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.length in 5..300 } }
        }.getOrDefault(emptyList())

        val known = current.mapTo(mutableListOf()) { QuestionQuality.stems(it.text) }
        val now = System.currentTimeMillis()
        var added = 0
        for (rule in proposed) {
            val stems = QuestionQuality.stems(rule)
            if (known.any { QuestionQuality.similarity(it, stems) >= 0.7 }) continue
            known += stems
            db.aiRules().insert(AiRuleEntity(text = rule, origin = "auto", createdAt = now + added))
            added++
        }
        db.replacementReasons().markProcessed(reasons.map { it.id })
        return added
    }
}
