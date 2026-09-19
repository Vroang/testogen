package com.testogen.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.launch

private fun formatBytes(size: Long): String = when {
    size >= 1024L * 1024L -> "${size / (1024L * 1024L)} МБ"
    size >= 1024L -> "${size / 1024L} КБ"
    else -> "$size Б"
}

// Шаг 27: экран входа.
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // Шаг 30: предложение восстановить учебники из облака после входа.
    var pendingRestore by remember { mutableStateOf<Int?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Вход в ТестоГен",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Библиотека учебников доступна после входа",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Email") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (loading) return@Button
                if (email.isBlank() || password.isBlank()) return@Button
                loading = true
                scope.launch {
                    val result = AuthManager.signIn(email.trim(), password)
                    loading = false
                    result.fold(
                        onSuccess = {
                            // Шаг 30: если в облаке учебников больше, чем
                            // на устройстве — предложить восстановление.
                            val cloud = runCatching {
                                TextbookRepository.cloudTextbookCount()
                            }.getOrDefault(0)
                            val local = (context.applicationContext as TestoGenApp)
                                .database
                                .textbookDao()
                                .countAll()
                            if (cloud > local) {
                                pendingRestore = cloud
                            } else {
                                onSignedIn()
                            }
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                context,
                                "Ошибка входа: ${e.message ?: "попробуйте ещё раз"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text = "Войти", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    pendingRestore?.let { count ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(text = "Восстановление") },
            text = {
                Text(
                    text = "Найдено $count учебников в облаке. Восстановить их на этом устройстве?",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(enabled = !restoring, onClick = {
                    restoring = true
                    scope.launch {
                        val result = TextbookRepository.restoreFromCloud(context)
                        restoring = false
                        pendingRestore = null
                        result.fold(
                            onSuccess = { n ->
                                Toast.makeText(
                                    context,
                                    "Восстановлено учебников: $n",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    context,
                                    "Не удалось восстановить: ${e.message ?: "ошибка"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        onSignedIn()
                    }
                }) {
                    Text(text = "Восстановить", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(enabled = !restoring, onClick = {
                    pendingRestore = null
                    onSignedIn()
                }) {
                    Text(text = "Не сейчас", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
