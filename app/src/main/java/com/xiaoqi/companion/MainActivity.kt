package com.xiaoqi.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaoqi.companion.core.local.LocalQwenBenchmarkRequest
import com.xiaoqi.companion.core.local.LocalQwenBenchmarkRunner
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.AndroidEntryPoint
import com.xiaoqi.companion.feature.chat.AuraHomeScreen
import com.xiaoqi.companion.feature.chat.AuraMotion
import com.xiaoqi.companion.feature.chat.ChatScreen
import com.xiaoqi.companion.feature.chat.ChatViewModel
import com.xiaoqi.companion.feature.chat.McpSettingsScreen
import com.xiaoqi.companion.feature.chat.MemoryRoomScreen
import com.xiaoqi.companion.feature.chat.SettingsScreen
import com.xiaoqi.companion.feature.onboarding.OnboardingScreen
import com.xiaoqi.companion.feature.onboarding.OnboardingViewModel
import com.xiaoqi.companion.ui.theme.CompanionTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val benchmarkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (maybeRunBenchmark(intent)) return

        setContent {
            CompanionTheme {
                AuraAppNavHost(viewModel = chatViewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeRunBenchmark(intent)
    }

    private fun maybeRunBenchmark(intent: android.content.Intent?): Boolean {
        if (intent?.action != ACTION_RUN_LOCAL_QWEN_BENCHMARK) return false
        AppLogger.info(LogTags.LocalModel, "local_qwen_benchmark_triggered")
        val request = LocalQwenBenchmarkRequest(
            modelName = intent.getStringExtra(EXTRA_MODEL_NAME),
            promptTokens = intent.getIntExtra(EXTRA_PROMPT_TOKENS, DEFAULT_PROMPT_TOKENS),
            decodeTokens = intent.getIntExtra(EXTRA_DECODE_TOKENS, DEFAULT_DECODE_TOKENS),
            warmupRuns = intent.getIntExtra(EXTRA_WARMUP_RUNS, DEFAULT_WARMUP_RUNS),
            measureRuns = intent.getIntExtra(EXTRA_MEASURE_RUNS, DEFAULT_MEASURE_RUNS),
            threadNum = intent.getIntExtra(EXTRA_THREAD_NUM, DEFAULT_THREAD_NUM).takeIf { it > 0 },
            backendType = intent.getStringExtra(EXTRA_BACKEND_TYPE)?.takeIf { it.isNotBlank() },
            precision = intent.getStringExtra(EXTRA_PRECISION)?.takeIf { it.isNotBlank() },
            memory = intent.getStringExtra(EXTRA_MEMORY)?.takeIf { it.isNotBlank() },
        )
        AppLogger.info(
            LogTags.LocalModel,
            "local_qwen_benchmark_request_parsed",
            "model" to request.modelName,
            "threadNum" to request.threadNum,
            "backendType" to request.backendType,
            "precision" to request.precision,
            "memory" to request.memory,
            "decodeTokens" to request.decodeTokens,
        )
        benchmarkScope.launch(Dispatchers.IO) {
            runCatching {
                val runner = LocalQwenBenchmarkRunner(applicationContext)
                runner.run(request).also { result ->
                    runner.writeResult(result)
                }
            }.onSuccess { result ->
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_benchmark_completed",
                    "model" to result.modelName,
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Benchmark done: ${result.modelName}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    finishAndRemoveTask()
                }
            }.onFailure { throwable ->
                LocalQwenBenchmarkRunner(applicationContext).writeFailure(throwable)
                AppLogger.error(
                    LogTags.LocalModel,
                    throwable,
                    "local_qwen_benchmark_failed",
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Benchmark failed: ${throwable.message ?: throwable::class.java.simpleName}",
                        Toast.LENGTH_LONG,
                    ).show()
                    finishAndRemoveTask()
                }
            }
        }
        return true
    }

    private companion object {
        const val ACTION_RUN_LOCAL_QWEN_BENCHMARK =
            "com.xiaoqi.companion.action.RUN_LOCAL_QWEN_BENCHMARK"
        const val EXTRA_MODEL_NAME = "modelName"
        const val EXTRA_PROMPT_TOKENS = "promptTokens"
        const val EXTRA_DECODE_TOKENS = "decodeTokens"
        const val EXTRA_WARMUP_RUNS = "warmupRuns"
        const val EXTRA_MEASURE_RUNS = "measureRuns"
        const val EXTRA_THREAD_NUM = "threadNum"
        const val EXTRA_BACKEND_TYPE = "backendType"
        const val EXTRA_PRECISION = "precision"
        const val EXTRA_MEMORY = "memory"
        const val DEFAULT_PROMPT_TOKENS = 256
        const val DEFAULT_DECODE_TOKENS = 64
        const val DEFAULT_WARMUP_RUNS = 1
        const val DEFAULT_MEASURE_RUNS = 3
        const val DEFAULT_THREAD_NUM = -1
    }
}

private object AuraRoutes {
    const val Home = "home"
    const val Chat = "chat"
    const val Settings = "settings"
    const val McpSettings = "settings/mcp"
    const val MemoryRoom = "memory-room"
    const val Onboarding = "onboarding"
}

@Composable
private fun AuraAppNavHost(
    viewModel: ChatViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val slideIn = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(AuraMotion.MediumMs),
    ) + fadeIn(animationSpec = tween(AuraMotion.ShortMs))
    val slideOut = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(AuraMotion.MediumMs),
    ) + fadeOut(animationSpec = tween(AuraMotion.ShortMs))
    val popSlideIn = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(AuraMotion.MediumMs),
    ) + fadeIn(animationSpec = tween(AuraMotion.ShortMs))
    val popSlideOut = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(AuraMotion.MediumMs),
    ) + fadeOut(animationSpec = tween(AuraMotion.ShortMs))

    NavHost(
        navController = navController,
        startDestination = AuraRoutes.Home,
        enterTransition = { slideIn },
        exitTransition = { slideOut },
        popEnterTransition = { popSlideIn },
        popExitTransition = { popSlideOut },
    ) {
        composable(
            route = AuraRoutes.Home,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            AuraHomeScreen(
                viewModel = viewModel,
                onOpenChat = { navController.navigate(AuraRoutes.Chat) },
                onOpenSettings = {
                    viewModel.prepareSettings()
                    navController.navigate(AuraRoutes.Settings)
                },
                onOpenMemoryRoom = { navController.navigate(AuraRoutes.MemoryRoom) },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.Chat) {
            ChatScreen(
                viewModel = viewModel,
                onOpenMemoryRoom = { navController.navigate(AuraRoutes.MemoryRoom) },
                onOpenSettings = {
                    viewModel.prepareSettings()
                    navController.navigate(AuraRoutes.Settings)
                },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.Settings) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.McpSettings) {
            McpSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AuraRoutes.MemoryRoom) {
            MemoryRoomScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AuraRoutes.Onboarding) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(AuraRoutes.Home) {
                        popUpTo(AuraRoutes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
    }

    // 启动判断:未完成 onboarding → 跳 Onboarding(走一次性,避免堆栈污染)
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isCompleted by onboardingViewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(isCompleted) {
        if (!isCompleted) {
            navController.navigate(AuraRoutes.Onboarding)
        }
    }
}
