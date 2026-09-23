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
            val thresholdHours = settingsRepository.get(KEY_INACTIVITY_HOURS)?.toLongOrNull()
                ?: DEFAULT_INACTIVITY_HOURS
            val thresholdMs = thresholdHours * 60 * 60 * 1000L
            if (now - lastMessage.createdAt < thresholdMs) return Result.success()

            // Limit re-engagement frequency so we don't spam if the user keeps ignoring
            val lastReengagement = settingsRepository.get(KEY_LAST_REENGAGEMENT)?.toLongOrNull() ?: 0L
            if (now - lastReengagement < thresholdMs * 2) return Result.success()

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
        const val KEY_INACTIVITY_HOURS = "reengagement_hours"
        const val DEFAULT_INACTIVITY_HOURS = 24L

        private const val KEY_LAST_REENGAGEMENT = "last_reengagement_at"

        const val WORK_NAME = "langtutor_reengagement"
    }
}
