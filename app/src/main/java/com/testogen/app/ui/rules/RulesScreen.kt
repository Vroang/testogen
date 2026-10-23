package com.testogen.app.ui.rules

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.AiException
import com.testogen.app.data.db.AiRuleEntity
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(private val c: AppContainer) : ViewModel() {
    val rules: StateFlow<List<AiRuleEntity>> = c.database.aiRules().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val newReasons: StateFlow<Int> = c.database.replacementReasons().observeUnprocessedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var draft by mutableStateOf("")
    var learning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun add() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        viewModelScope.launch {
            c.database.aiRules().insert(AiRuleEntity(text = text, origin = "manual", createdAt = System.currentTimeMillis()))
        }
    }

    fun toggle(rule: AiRuleEntity) = viewModelScope.launch { c.database.aiRules().update(rule.copy(active = !rule.active)) }
    fun delete(rule: AiRuleEntity) = viewModelScope.launch { c.database.aiRules().delete(rule.id) }

    fun learn() {
        if (learning) return
        learning = true
        message = null
        viewModelScope.launch {
            message = try {
                val added = c.rules.learnFromReasons()
                if (added == 0) "Новых правил не понадобилось" else "Добавлено $added ${plural(added, "правило", "правила", "правил")}"
            } catch (e: AiException) {
                e.message
            }
            learning = false
        }
    }
}

/** Правила «Чего не делать» — учитываются при каждой генерации. Только дополняются. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: RulesViewModel = viewModel { RulesViewModel(container) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val newReasons by vm.newReasons.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { BackHeader("Правила для нейросети", onBack) }
        item(key = "intro") {
            Text(
                "Нейросеть соблюдает эти правила при каждом создании вопросов. Новые правила появляются из причин, по которым вы заменяете вопросы в тестах, — старые при этом не стираются.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(horizontal = pad),
            )
        }
        if (newReasons > 0 || vm.message != null) {
            item(key = "learn") {
                SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad), color = Palette.PrimarySoft) {
                    if (newReasons > 0) {
                        Text(
                            "Новых причин замен: $newReasons",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.OnPrimarySoft,
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(if (vm.learning) "Думаю…" else "Сделать из них правила", vm::learn, enabled = !vm.learning)
                    }
                    vm.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.OnPrimarySoft)
                    }
                }
            }
        }
        item(key = "add") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Text("Своё правило", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    vm.draft,
                    { vm.draft = it },
                    placeholder = "Например: не спрашивай точные даты рождения",
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Добавить", vm::add, enabled = vm.draft.isNotBlank())
            }
        }
        if (rules.isNotEmpty()) {
            item(key = "label") {
                SectionLabel("Действующие правила", Modifier.padding(horizontal = pad))
            }
        }
        items(rules, key = { it.id }, contentType = { "rule" }) { rule ->
            SoftCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pad)
                    .animateItem(fadeInSpec = tween(300), placementSpec = tween(300), fadeOutSpec = tween(200)),
                contentPadding = PaddingValues(start = 18.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (rule.active) Palette.Text else Palette.TextHint,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = rule.active,
                        onCheckedChange = { vm.toggle(rule) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.SurfaceSunken),
                    )
                    IconButton(onClick = { vm.delete(rule) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить правило", tint = Palette.TextSecondary)
                    }
                }
            }
        }
    }
}
