package com.langtutor.android.ui.wordreview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReviewScreen(
    onBack: () -> Unit,
    viewModel: WordReviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))

                state.tooFewWords -> Column(
                    modifier = Modifier.padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Add at least ${WordReviewViewModel.MIN_WORDS_REQUIRED} words to your dictionary to start reviewing.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onBack) { Text("Go back") }
                }

                state.error != null -> Column(
                    modifier = Modifier.padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = { viewModel.loadNextWord() }) { Text("Retry") }
                }

                state.term.isNotEmpty() -> ReviewContent(
                    state = state,
                    onSelect = viewModel::submitAnswer,
                    onNext = viewModel::loadNextWord,
                )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    state: WordReviewUiState,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Attempt tally
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            val total = state.correctCount + state.incorrectCount
            Text(
                text = if (total == 0) "Not yet reviewed" else "${state.correctCount}/${total} correct",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Term
        Text(
            text = state.term,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        Text(
            text = "Which definition is correct?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Option cards
        state.options.forEach { option ->
            OptionCard(
                text = option,
                answered = state.answered,
                isCorrect = option == state.correctDefinition,
                isSelected = option == state.selectedOption,
                onClick = { if (!state.answered) onSelect(option) },
            )
        }

        // Result feedback
        if (state.answered) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.isCorrect == true) "Correct!" else "Incorrect — the right answer is highlighted.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isCorrect == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Next word") }
        }
    }
}

@Composable
private fun OptionCard(
    text: String,
    answered: Boolean,
    isCorrect: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        !answered -> MaterialTheme.colorScheme.surfaceVariant
        isCorrect -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !answered -> MaterialTheme.colorScheme.onSurfaceVariant
        isCorrect -> MaterialTheme.colorScheme.onPrimaryContainer
        isSelected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        onClick = onClick,
        enabled = !answered,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isSelected && !answered) 4.dp else 1.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
