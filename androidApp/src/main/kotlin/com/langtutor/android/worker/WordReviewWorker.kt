package com.langtutor.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.langtutor.android.notification.NotificationHelper
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.VocabRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WordReviewWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val profileRepository: ProfileRepository by inject()
    private val vocabRepository: VocabRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            val profile = profileRepository.getActive() ?: return Result.success()
            val count = vocabRepository.countByProfile(profile.id)
            if (count < 4) return Result.success()
            val word = vocabRepository.getRandomForReview(profile.id) ?: return Result.success()
            NotificationHelper.showWordReviewNotification(applicationContext, word.term)
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "langtutor_word_review"
    }
}
