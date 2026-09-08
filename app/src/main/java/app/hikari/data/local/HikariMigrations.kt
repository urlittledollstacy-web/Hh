package app.hikari.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `hikari_favorites` (
                `mediaId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `coverUrl` TEXT,
                `averageScore` INTEGER,
                `episodesOrChapters` INTEGER,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`mediaId`, `type`)
            )
            """.trimIndent(),
        )
    }
}
