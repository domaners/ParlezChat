package com.langtutor.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.langtutor.android.notification.NotificationHelper
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.SettingsRepository
import com.langtutor.domain.usecase.GenerateEngagementMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val profileRepository: ProfileRepository by inject()
    private val messageRepository: MessageRepository by inject()
    private val generateEngagementMessage: GenerateEngagementMessage by inject()
    private val settingsRepository: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            val profile = profileRepository.getActive() ?: return Result.success()

            // Use the most recent SENT message to measure inactivity
            val lastMessage = messageRepository.getHistoryWindow(profile.id, 1L).firstOrNull()
                ?: return Result.success()  // no conversation started yet

            val now = System.currentTimeMillis()
            if (now - lastMessage.createdAt < INACTIVITY_THRESHOLD_MS) return Result.success()

            // Limit re-engagement frequency so we don't spam if the user keeps ignoring
            val lastReengagement = settingsRepository.get(KEY_LAST_REENGAGEMENT)?.toLongOrNull() ?: 0L
            if (now - lastReengagement < MIN_REENGAGEMENT_INTERVAL_MS) return Result.success()

            generateEngagementMessage(profile).fold(
                onSuccess = { message ->
                    settingsRepository.set(KEY_LAST_REENGAGEMENT, now.toString())
                    NotificationHelper.showReEngagementNotification(
                        context = applicationContext,
                        languageName = profile.targetLanguage,
                        messageText = message.content,
                    )
                },
                onFailure = { /* Silent — the message wasn't stored, try again next run */ },
            )
            Result.success()
        } catch (e: Exception) {
            // Don't retry on exception; the next periodic run will try again
            Result.success()
        }
    }

    companion object {
        /** How long the user must be inactive before we send a re-engagement message. */
        private const val INACTIVITY_THRESHOLD_MS = 24 * 60 * 60 * 1000L  // 24 hours

        /** Minimum gap between two re-engagement messages to avoid spamming ignored chats. */
        private const val MIN_REENGAGEMENT_INTERVAL_MS = 48 * 60 * 60 * 1000L  // 48 hours

        private const val KEY_LAST_REENGAGEMENT = "last_reengagement_at"

        const val WORK_NAME = "langtutor_reengagement"
    }
}
