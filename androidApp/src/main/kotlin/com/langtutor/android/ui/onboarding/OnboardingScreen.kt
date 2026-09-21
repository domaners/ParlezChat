package com.langtutor.android.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.model.displayName
import com.langtutor.domain.model.labelForUi
import org.koin.androidx.compose.koinViewModel

private val COMMON_LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese",
    "Polish", "Dutch", "Russian", "Japanese", "Chinese (Mandarin)",
    "Korean", "Arabic", "Turkish", "Swedish", "Norwegian", "Danish",
    "Finnish", "Hebrew", "Hindi",
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                1 -> ApiKeyStep(
                    isLoading = state.isLoading,
                    error = state.error,
                    onSubmit = viewModel::validateApiKey,
                    onDismissError = viewModel::dismissError,
                )
                2 -> LanguagePickerStep(
                    title = "What is your native language?",
                    languages = COMMON_LANGUAGES,
                    onSelect = viewModel::setNativeLanguage,
                )
                3 -> LanguagePickerStep(
                    title = "What language do you want to learn?",
                    languages = COMMON_LANGUAGES.filter { it != state.nativeLanguage },
                    error = state.error,
                    onSelect = viewModel::setTargetLanguage,
                )
                4 -> InterestsStep(
                    onSubmit = viewModel::setInterests,
                )
                5 -> LevelStep(onSelect = viewModel::setLevel)
                6 -> StudyDurationStep(onSelect = viewModel::setStudyDuration)
            }
        }
    }
}

@Composable
private fun ApiKeyStep(
    isLoading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var key by remember { mutableStateOf("") }

    Text("Enter your Claude API key", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Your key is encrypted on this device using Android Keystore. " +
        "We recommend creating a dedicated key with a spend limit in your Anthropic console.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = key,
        onValueChange = { key = it; onDismissError() },
        label = { Text("API key (sk-ant-…)") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank()) onSubmit(key) }),
        isError = error != null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onSubmit(key) },
        enabled = key.isNotBlank() && !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Validate & Continue")
    }
}

@Composable
private fun LanguagePickerStep(
    title: String,
    languages: List<String>,
    error: String? = null,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    languages.forEach { lang ->
        OutlinedButton(
            onClick = { onSelect(lang) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text(lang) }
    }
}

@Composable
private fun InterestsStep(onSubmit: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("What are your interests?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text("Enter topics separated by commas (e.g. cooking, travel, music).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Interests") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            val interests = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            onSubmit(interests)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
}

@Composable
private fun LevelStep(onSelect: (ProficiencyLevel) -> Unit) {
    Text("What's your current level?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    ProficiencyLevel.entries.forEach { level ->
        OutlinedButton(
            onClick = { onSelect(level) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text(level.displayName()) }
    }
}

@Composable
private fun StudyDurationStep(onSelect: (StudyDuration) -> Unit) {
    Text("How long have you been studying?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    StudyDuration.entries.forEach { duration ->
        OutlinedButton(
            onClick = { onSelect(duration) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text(duration.labelForUi()) }
    }
}
