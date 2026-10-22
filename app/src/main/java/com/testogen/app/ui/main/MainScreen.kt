package com.testogen.app.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.testogen.app.ui.bank.BankScreen
import com.testogen.app.ui.components.softShadow
import com.testogen.app.ui.tests.TestsScreen
import com.testogen.app.ui.textbooks.TextbooksScreen
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.Palette
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: () -> ImageVector) {
    TESTS("Тесты", { AppIcons.Document }),
    BANK("Вопросы", { Icons.AutoMirrored.Filled.List }),
    TEXTBOOKS("Учебники", { AppIcons.Book }),
}

/** Главный экран с тремя вкладками. Состояние каждой вкладки (прокрутка и т. п.) сохраняется при переключении. */
@Composable
fun MainScreen(onOpenSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TESTS) }
    val stateHolder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = remember {
        { text -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = Palette.Surface,
                tonalElevation = 0.dp,
                modifier = Modifier.softShadow(RectangleShape, elevation = 12.dp),
            ) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon(), contentDescription = null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.OnPrimarySoft,
                            selectedTextColor = Palette.OnPrimarySoft,
                            indicatorColor = Palette.PrimarySoft,
                            unselectedIconColor = Palette.TextSecondary,
                            unselectedTextColor = Palette.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(
            targetState = tab,
            animationSpec = tween(220),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "tabs",
        ) { current ->
            stateHolder.SaveableStateProvider(current.name) {
                when (current) {
                    Tab.TESTS -> TestsScreen(onOpenSettings = onOpenSettings, showMessage = showMessage)
                    Tab.BANK -> BankScreen(onOpenSettings = onOpenSettings, showMessage = showMessage)
                    Tab.TEXTBOOKS -> TextbooksScreen(onOpenSettings = onOpenSettings, showMessage = showMessage)
                }
            }
        }
    }
}
