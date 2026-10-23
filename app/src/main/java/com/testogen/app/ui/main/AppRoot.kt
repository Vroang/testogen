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
import androidx.navigation.toRoute
import com.testogen.app.ui.bank.QuestionEditScreen
import com.testogen.app.ui.bank.TopicQuestionsScreen
import com.testogen.app.ui.generate.GenerateTopicScreen
import com.testogen.app.ui.generate.JobScreen
import com.testogen.app.ui.rules.RulesScreen
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.ailog.AiLogScreen
import com.testogen.app.ui.export.ExportScreen
import com.testogen.app.ui.settings.SettingsScreen
import com.testogen.app.ui.tests.TestBuilderScreen
import com.testogen.app.ui.tests.TestEditorScreen
import com.testogen.app.ui.textbooks.GenerateTextbookScreen
import com.testogen.app.ui.textbooks.PdfPageScreen
import com.testogen.app.ui.textbooks.SectionReaderScreen
import com.testogen.app.ui.textbooks.TextbookScreen
import com.testogen.app.data.db.QuestionEntity
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute

@Serializable
data object SettingsRoute

@Serializable
data object AiLogRoute

@Serializable
data object RulesRoute

@Serializable
data class GenerateTopicRoute(val topic: String = "")

@Serializable
data class JobRoute(val jobId: Long)

@Serializable
data class TopicRoute(val key: String, val title: String)

@Serializable
data class QuestionEditRoute(val id: Long = 0, val topic: String = "")

@Serializable
data class TextbookRoute(val id: Long)

@Serializable
data class ReaderRoute(val sectionId: Long, val start: Int = -1, val end: Int = -1, val query: String = "")

@Serializable
data class PdfPageRoute(val textbookId: Long, val page: Int)

@Serializable
data class GenerateTextbookRoute(val textbookId: Long, val preselect: Long = 0)

@Serializable
data object TestBuilderRoute

@Serializable
data class TestEditorRoute(val id: Long)

@Serializable
data class ExportRoute(val testId: Long)

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
fun AppRoot(debugTopic: String? = null) {
    val container = LocalAppContainer.current
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        container.updater.checkOnce()
        if (debugTopic != null) nav.navigate(GenerateTopicRoute(debugTopic))
    }

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
            MainScreen(
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onGenerate = { nav.navigate(GenerateTopicRoute(it)) },
                onAddManual = { nav.navigate(QuestionEditRoute(topic = it)) },
                onOpenTopic = { key, title -> nav.navigate(TopicRoute(key, title)) },
                onOpenJob = { nav.navigate(JobRoute(it)) },
                onOpenTextbook = { nav.navigate(TextbookRoute(it)) },
                onNewTest = { nav.navigate(TestBuilderRoute) },
                onOpenTest = { nav.navigate(TestEditorRoute(it)) },
            )
        }
        composable<TestBuilderRoute> {
            TestBuilderScreen(
                onBack = { nav.popBackStack() },
                onBuilt = { id -> nav.navigate(TestEditorRoute(id)) { popUpTo<TestBuilderRoute> { inclusive = true } } },
            )
        }
        composable<TestEditorRoute> { entry ->
            val route = entry.toRoute<TestEditorRoute>()
            TestEditorScreen(
                testId = route.id,
                onBack = { nav.popBackStack() },
                onEditQuestion = { nav.navigate(QuestionEditRoute(id = it)) },
                onSource = { q -> openSource(nav, q) },
                onExport = { nav.navigate(ExportRoute(it)) },
            )
        }
        composable<ExportRoute> { entry ->
            ExportScreen(testId = entry.toRoute<ExportRoute>().testId, onBack = { nav.popBackStack() })
        }
        composable<TextbookRoute> { entry ->
            val route = entry.toRoute<TextbookRoute>()
            TextbookScreen(
                textbookId = route.id,
                onBack = { nav.popBackStack() },
                onOpenSection = { id, start, end, query -> nav.navigate(ReaderRoute(id, start, end, query)) },
                onGenerate = { nav.navigate(GenerateTextbookRoute(it)) },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            SectionReaderScreen(
                sectionId = route.sectionId,
                highlightStart = route.start,
                highlightEnd = route.end,
                query = route.query,
                onBack = { nav.popBackStack() },
                onOpenPdfPage = { book, page -> nav.navigate(PdfPageRoute(book, page)) },
            )
        }
        composable<PdfPageRoute> { entry ->
            val route = entry.toRoute<PdfPageRoute>()
            PdfPageScreen(textbookId = route.textbookId, pdfPage = route.page, onBack = { nav.popBackStack() })
        }
        composable<GenerateTextbookRoute> { entry ->
            val route = entry.toRoute<GenerateTextbookRoute>()
            GenerateTextbookScreen(
                textbookId = route.textbookId,
                preselect = route.preselect,
                onBack = { nav.popBackStack() },
                onStarted = { id -> nav.navigate(JobRoute(id)) { popUpTo<GenerateTextbookRoute> { inclusive = true } } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAiLog = { nav.navigate(AiLogRoute) },
                onOpenRules = { nav.navigate(RulesRoute) },
            )
        }
        composable<RulesRoute> {
            RulesScreen(onBack = { nav.popBackStack() })
        }
        composable<GenerateTopicRoute> { entry ->
            val route = entry.toRoute<GenerateTopicRoute>()
            GenerateTopicScreen(
                initialTopic = route.topic,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                // Экран настройки заменяется экраном хода генерации: «назад» ведёт туда, откуда пришли
                onStarted = { id -> nav.navigate(JobRoute(id)) { popUpTo<GenerateTopicRoute> { inclusive = true } } },
            )
        }
        composable<JobRoute> { entry ->
            val route = entry.toRoute<JobRoute>()
            JobScreen(
                jobId = route.jobId,
                onBack = { nav.popBackStack() },
                onOpenTopic = { key, title ->
                    nav.navigate(TopicRoute(key, title)) { popUpTo<JobRoute> { inclusive = true } }
                },
                onEditQuestion = { nav.navigate(QuestionEditRoute(id = it)) },
                onSource = { q -> openSource(nav, q) },
            )
        }
        composable<TopicRoute> { entry ->
            val route = entry.toRoute<TopicRoute>()
            TopicQuestionsScreen(
                topicKey = route.key,
                title = route.title,
                onBack = { nav.popBackStack() },
                onGenerateMore = { nav.navigate(GenerateTopicRoute(it)) },
                onAddManual = { nav.navigate(QuestionEditRoute(topic = it)) },
                onEdit = { nav.navigate(QuestionEditRoute(id = it)) },
                onSource = { q -> openSource(nav, q) },
            )
        }
        composable<QuestionEditRoute> { entry ->
            val route = entry.toRoute<QuestionEditRoute>()
            QuestionEditScreen(id = route.id, defaultTopic = route.topic, onBack = { nav.popBackStack() })
        }
        composable<AiLogRoute> {
            AiLogScreen(onBack = { nav.popBackStack() })
        }
    }

    UpdateDialog()
}

/** «Откуда вопрос»: параграф учебника с подсвеченной цитатой. */
private fun openSource(nav: NavController, q: QuestionEntity) {
    val section = q.sectionId ?: return
    nav.navigate(ReaderRoute(section, q.quoteStart ?: -1, q.quoteEnd ?: -1, if (q.quoteStart == null) q.quote.orEmpty() else ""))
}
