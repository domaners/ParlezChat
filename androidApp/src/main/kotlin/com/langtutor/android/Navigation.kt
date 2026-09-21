package com.langtutor.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.langtutor.android.ui.changelanguage.ChangeLanguageScreen
import com.langtutor.android.ui.chat.ChatScreen
import com.langtutor.android.ui.dictionary.DictionaryScreen
import com.langtutor.android.ui.errorlog.ErrorLogScreen
import com.langtutor.android.ui.onboarding.OnboardingScreen
import com.langtutor.android.ui.settings.SettingsScreen
import com.langtutor.domain.repository.SettingsRepository
import org.koin.compose.koinInject

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Dictionary : Screen("dictionary")
    object Settings : Screen("settings")
    object ChangeLanguage : Screen("change_language")
    object ErrorLog : Screen("error_log")
}

@Composable
fun LangTutorNavHost() {
    val settingsRepository = koinInject<SettingsRepository>()
    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        onboardingComplete = settingsRepository.get("onboarding_complete") == "true"
    }

    // Wait until we know the onboarding state
    val complete = onboardingComplete ?: return

    if (!complete) {
        OnboardingScreen(
            onComplete = { onboardingComplete = true }
        )
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Chat.route) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToDictionary = { navController.navigate(Screen.Dictionary.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToChangeLanguage = { navController.navigate(Screen.ChangeLanguage.route) },
                onNavigateToErrorLog = { navController.navigate(Screen.ErrorLog.route) },
            )
        }
        composable(Screen.Dictionary.route) {
            DictionaryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onKeyRemoved = { onboardingComplete = false },
            )
        }
        composable(Screen.ChangeLanguage.route) {
            ChangeLanguageScreen(
                onBack = { navController.popBackStack() },
                onLanguageChanged = { navController.popBackStack(Screen.Chat.route, false) },
            )
        }
        composable(Screen.ErrorLog.route) {
            ErrorLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
