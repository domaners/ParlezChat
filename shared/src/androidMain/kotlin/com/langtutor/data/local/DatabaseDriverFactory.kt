package com.langtutor.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        val driver = AndroidSqliteDriver(LangTutorDatabase.Schema, context, "langtutor.db")
        // Enable foreign key constraints; SQLite disables them by default.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return driver
    }
}
