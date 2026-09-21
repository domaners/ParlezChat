package com.langtutor.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.langtutor.domain.model.LearnerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplanationSheet(
    sheetState: ExplanationSheetState,
    profile: LearnerProfile?,
    onDismiss: () -> Unit,
    onSaveWord: (String) -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Original message", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(sheetState.message.content, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            when {
                sheetState.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                sheetState.error != null -> {
                    Text(sheetState.error, color = MaterialTheme.colorScheme.error)
                }
                sheetState.explanation != null -> {
                    val explanation = sheetState.explanation

                    Text("Translation", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(explanation.translation, fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodyMedium)

                    if (explanation.grammarNotes.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Grammar & vocabulary", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        explanation.grammarNotes.forEach { note ->
                            Text(note.theme, style = MaterialTheme.typography.labelSmall)
                            Text(note.note, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                        }
                    }

                    // Save-word prompt
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    var wordInput by remember { mutableStateOf("") }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = wordInput,
                            onValueChange = { wordInput = it },
                            label = { Text("Save a word…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSaveWord(wordInput.trim()); wordInput = "" },
                            enabled = wordInput.isNotBlank(),
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
