package com.testogen.app.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.ailog.AiLogScreen
import com.testogen.app.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute

@Serializable
data object SettingsRoute

@Serializable
data object AiLogRoute

/** Плавное «выразительное» замедление, как в Material 3. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val ENTER_MS = 340
private const val EXIT_MS = 220

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(ENTER_MS, easing = Emphasized)) +
        slideInHorizontally(tween(ENTER_MS, easing = Emphasized)) { it / 5 }
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(ENTER_MS, easing = Emphasized)) { -it / 12 }
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(ENTER_MS, easing = Emphasized)) +
        slideInHorizontally(tween(ENTER_MS, easing = Emphasized)) { -it / 12 }
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(ENTER_MS, easing = Emphasized)) { it / 5 }
}

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val nav = rememberNavController()

    LaunchedEffect(Unit) { container.updater.checkOnce() }

    NavHost(
        navController = nav,
        startDestination = MainRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable<MainRoute> {
            MainScreen(onOpenSettings = { nav.navigate(SettingsRoute) })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAiLog = { nav.navigate(AiLogRoute) },
            )
        }
        composable<AiLogRoute> {
            AiLogScreen(onBack = { nav.popBackStack() })
        }
    }

    UpdateDialog()
}
