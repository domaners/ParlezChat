package com.langtutor.android.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.Role
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToDictionary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChangeLanguage: () -> Unit,
    onNavigateToErrorLog: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("LangTutor", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("My dictionary") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToDictionary() },
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                )
                NavigationDrawerItem(
                    label = { Text("Change language") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToChangeLanguage() },
                )
                NavigationDrawerItem(
                    label = { Text("Error log") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToErrorLog() },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.profile?.targetLanguage ?: "Chat") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                )
            },
            snackbarHost = {},
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                // Banner
                state.banner?.let { banner ->
                    BannerBar(
                        banner = banner,
                        onDismiss = viewModel::dismissBanner,
                        onSettings = onNavigateToSettings,
                    )
                }

                // Messages
                val listState = rememberLazyListState()
                LaunchedEffect(state.messages.size) {
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onLongPress = { if (msg.role == Role.ASSISTANT) viewModel.openExplanation(msg) },
                            onRetry = { viewModel.retry(msg) },
                        )
                    }
                    if (state.isTyping) {
                        item { TypingIndicator() }
                    }
                }

                // Input bar
                MessageInput(
                    enabled = state.isSendEnabled,
                    isSavingWord = state.isSavingWord,
                    onSend = viewModel::send,
                    onSaveWord = viewModel::saveWordFromInput,
                )
            }
        }
    }

    // Explanation bottom sheet
    state.explanation?.let { sheet ->
        ExplanationSheet(
            sheetState = sheet,
            profile = state.profile,
            onDismiss = viewModel::closeExplanation,
            onSaveWord = { term -> viewModel.saveWord(term, sheet.message) },
        )
    }

    // Saved-word confirmation snackbar
    state.savedWordToast?.let { entry ->
        LaunchedEffect(entry) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissSavedWordToast()
        }
        Snackbar(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentSize(),
            action = { TextButton(onClick = viewModel::dismissSavedWordToast) { Text("OK") } },
        ) { Text("\"${entry.term}\" saved to your dictionary.") }
    }
}

private val MSG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

/** Splits a bot message into (mainText, translation) if a `---` separator line is present. */
private fun splitTranslation(content: String): Pair<String, String?> {
    val lines = content.lines()
    val sepIndex = lines.indexOfFirst { it.trim() == "---" }
    if (sepIndex < 0) return Pair(content, null)
    val main = lines.take(sepIndex).joinToString("\n").trim()
    val translation = lines.drop(sepIndex + 1).joinToString("\n").trim()
    return Pair(main, translation.ifEmpty { null })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
) {
    val isUser = message.role == Role.USER
    val timestamp = remember(message.createdAt) {
        MSG_TIME_FORMATTER.format(Instant.ofEpochMilli(message.createdAt))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            val (mainText, translation) = remember(message.content) {
                splitTranslation(message.content)
            }
            val bubbleContentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(onLongClick = onLongPress, onClick = {}),
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = mainText,
                            color = bubbleContentColor,
                        )
                        if (translation != null) {
                            Text(
                                text = translation,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = bubbleContentColor.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            if (message.status == DeliveryStatus.FAILED) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tap to retry", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(start = 4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun MessageInput(
    enabled: Boolean,
    isSavingWord: Boolean,
    onSend: (String) -> Unit,
    onSaveWord: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Message…") },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            enabled = enabled,
        )
        Spacer(Modifier.width(4.dp))
        // Save-to-dictionary: type a word and tap the bookmark icon
        if (isSavingWord) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) { onSaveWord(trimmed); text = "" }
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "Save to dictionary",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) { onSend(trimmed); text = "" }
            },
            enabled = enabled && text.isNotBlank(),
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun BannerBar(
    banner: BannerState,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                banner.message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            if (banner.showFixInSettings) {
                TextButton(onClick = onSettings) { Text("Fix in Settings") }
            }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
