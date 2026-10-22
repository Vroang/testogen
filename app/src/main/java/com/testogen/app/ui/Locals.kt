package com.testogen.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.testogen.app.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }
