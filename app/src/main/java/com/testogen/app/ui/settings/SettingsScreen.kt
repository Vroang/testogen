package com.testogen.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.BuildConfig
import com.testogen.app.data.prefs.KeysRepository
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.IconBadge
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val keys: KeysRepository) : ViewModel() {
    var deepSeek by mutableStateOf("")
        private set
    var openRouter by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            deepSeek = keys.deepSeekKey.first()
            openRouter = keys.openRouterKey.first()
            loaded = true
        }
    }

    fun onDeepSeekChange(raw: String) {
        deepSeek = KeysRepository.cleanKey(raw)
        viewModelScope.launch { keys.setDeepSeekKey(deepSeek) }
    }

    fun onOpenRouterChange(raw: String) {
        openRouter = KeysRepository.cleanKey(raw)
        viewModelScope.launch { keys.setOpenRouterKey(openRouter) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAiLog: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container.keys) }
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header") { BackHeader("Настройки", onBack) }
        item(key = "ai-label") { SectionLabel("Нейросеть", Modifier.padding(horizontal = pad)) }
        item(key = "keys") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Key, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Ключи доступа", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
                KeyField(
                    label = "DeepSeek — основной",
                    hint = "Ключ с сайта platform.deepseek.com, раздел API keys",
                    value = vm.deepSeek,
                    enabled = vm.loaded,
                    onChange = vm::onDeepSeekChange,
                )
                Spacer(Modifier.height(18.dp))
                KeyField(
                    label = "OpenRouter — запасной",
                    hint = "Используется, если DeepSeek недоступен. Ключ с openrouter.ai/keys",
                    value = vm.openRouter,
                    enabled = vm.loaded,
                    onChange = vm::onOpenRouterChange,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ключи хранятся только на этом телефоне и не попадают в резервные копии.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "log") {
            SoftCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                onClick = onOpenAiLog,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Receipt, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Журнал запросов", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Сколько потрачено на нейросеть",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextSecondary)
                }
            }
        }
        item(key = "version") {
            Text(
                "ТестоГен ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextHint,
                modifier = Modifier.padding(horizontal = pad, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun KeyField(
    label: String,
    hint: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text("Вставьте ключ") },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        trailingIcon = {
            if (value.isNotEmpty()) {
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") }
            }
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Palette.Background,
            focusedContainerColor = Palette.Surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
