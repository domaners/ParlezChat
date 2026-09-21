package com.langtutor.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.model.displayName
import com.langtutor.domain.model.labelForUi
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onKeyRemoved: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Feedback messages
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            if (profile != null) {
                // Native language (editable)
                var nativeLang by remember(profile) { mutableStateOf(profile.nativeLanguage) }
                OutlinedTextField(
                    value = nativeLang,
                    onValueChange = { nativeLang = it },
                    label = { Text("Native language") },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Target language — not editable here, shown for info only
                OutlinedTextField(
                    value = profile.targetLanguage,
                    onValueChange = {},
                    label = { Text("Target language (use Change Language to switch)") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Interests
                var interests by remember(profile) { mutableStateOf(profile.interests.joinToString(", ")) }
                OutlinedTextField(
                    value = interests,
                    onValueChange = { interests = it },
                    label = { Text("Interests (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )

                // Level picker
                var level by remember(profile) { mutableStateOf(profile.level) }
                Text("Level", style = MaterialTheme.typography.labelMedium)
                ProficiencyLevel.entries.forEach { l ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = level == l, onClick = { level = l })
                        Text(l.displayName())
                    }
                }

                // Study duration picker
                var duration by remember(profile) { mutableStateOf(profile.studyDuration) }
                Text("How long studying", style = MaterialTheme.typography.labelMedium)
                StudyDuration.entries.forEach { d ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = duration == d, onClick = { duration = d })
                        Text(d.labelForUi())
                    }
                }

                Button(
                    onClick = {
                        val parsedInterests = interests.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        viewModel.updateProfile(nativeLang, parsedInterests, level, duration)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                ) { Text("Save changes") }
            }

            HorizontalDivider()

            // API key section
            Text("API key", style = MaterialTheme.typography.titleMedium)
            if (state.hasApiKey) {
                Text("Current key: ${state.apiKeyMasked}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                var newKey by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("New key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.replaceApiKey(newKey) },
                        enabled = newKey.isNotBlank() && !state.isLoading,
                    ) { Text("Replace") }
                    OutlinedButton(
                        onClick = { viewModel.removeApiKey(onKeyRemoved) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                }
            }

            HorizontalDivider()

            // Advanced: model override
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            ModelDropdown(
                selected = state.modelOverride,
                onSelected = { viewModel.setModelOverride(it) },
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private val AVAILABLE_MODELS = listOf(
    "" to "Default (claude-sonnet-4-6)",
    "claude-opus-4-6" to "Claude Opus 4.6",
    "claude-sonnet-4-6" to "Claude Sonnet 4.6",
    "claude-haiku-4-5-20251001" to "Claude Haiku 4.5",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = AVAILABLE_MODELS.firstOrNull { it.first == selected }?.second
        ?: AVAILABLE_MODELS.first().second

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Claude model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AVAILABLE_MODELS.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(id); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
