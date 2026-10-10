package com.vitorkrichter.ordo.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vitorkrichter.ordo.core.database.migration.OrdoMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideOrdoDatabase(
        @ApplicationContext context: Context,
    ): OrdoDatabase = Room.databaseBuilder(
        context = context,
        klass = OrdoDatabase::class.java,
        name = OrdoDatabase.NAME,
    )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_single_in_progress`
                    ON `workout_sessions` (`status`)
                    WHERE `status` = 'IN_PROGRESS'
                    """.trimIndent(),
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO `user_profile` (`id`, `display_name`, `birth_date`, `height_cm`, `created_at`, `updated_at`) VALUES ('default', NULL, NULL, NULL, $now, $now)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `hydration_settings` (`id`, `target_ml`, `target_mode`, `created_at`, `updated_at`) VALUES ('default', 2000, 'DEFAULT_REFERENCE', $now, $now)",
                )
            }
        })
        .addMigrations(*OrdoMigrations.ALL)
        .build()
}
