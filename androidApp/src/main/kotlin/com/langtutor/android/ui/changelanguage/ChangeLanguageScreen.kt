package com.langtutor.android.ui.changelanguage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeLanguageScreen(
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit,
    viewModel: ChangeLanguageViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onLanguageChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isAddingLanguage) "Add language" else "Change language") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!state.isAddingLanguage) {
                LazyColumn {
                    items(state.profiles) { profile ->
                        val isActive = profile.isActive
                        ListItem(
                            headlineContent = { Text(profile.targetLanguage) },
                            supportingContent = { Text(profile.level.displayName()) },
                            trailingContent = {
                                if (isActive) Icon(Icons.Default.Check, "Active")
                            },
                            modifier = if (!isActive) Modifier.clickable { viewModel.selectProfile(profile.id) } else Modifier,
                        )
                        HorizontalDivider()
                    }
                    item {
                        TextButton(
                            onClick = viewModel::startAddLanguage,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("+ Add a language") }
                    }
                }
            } else {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                when (state.addStep) {
                    1 -> LanguagePickerStep(
                        title = "Which language do you want to learn?",
                        languages = COMMON_LANGUAGES.filter { it != state.nativeLanguage },
                        onSelect = viewModel::setTargetLanguage,
                    )
                    2 -> InterestsStep(onSubmit = viewModel::setInterests)
                    3 -> LevelStep(onSelect = viewModel::setLevel)
                    4 -> DurationStep(onSelect = viewModel::setStudyDuration)
                }
                if (state.isLoading) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerStep(title: String, languages: List<String>, onSelect: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    languages.forEach { lang ->
        OutlinedButton(onClick = { onSelect(lang) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(lang)
        }
    }
}

@Composable
private fun InterestsStep(onSubmit: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("What are your interests?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = text, onValueChange = { text = it },
        label = { Text("Interests (comma-separated)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { onSubmit(text.split(",").map { it.trim() }.filter { it.isNotEmpty() }) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
}

@Composable
private fun LevelStep(onSelect: (ProficiencyLevel) -> Unit) {
    Text("Your level in this language?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    ProficiencyLevel.entries.forEach { level ->
        OutlinedButton(onClick = { onSelect(level) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(level.displayName())
        }
    }
}

@Composable
private fun DurationStep(onSelect: (StudyDuration) -> Unit) {
    Text("How long have you been studying it?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    StudyDuration.entries.forEach { d ->
        OutlinedButton(onClick = { onSelect(d) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(d.labelForUi())
        }
    }
}
