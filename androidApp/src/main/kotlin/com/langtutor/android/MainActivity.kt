package com.langtutor.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.langtutor.android.notification.NotificationHelper
import com.langtutor.android.ui.theme.LangTutorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startAtReview = intent.getBooleanExtra(NotificationHelper.EXTRA_SHOW_REVIEW, false)
        setContent {
            LangTutorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LangTutorNavHost(startAtReview = startAtReview)
                }
            }
        }
    }
}
