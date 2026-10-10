package com.vitorkrichter.ordo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object OrdoMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exercises` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `normalized_name` TEXT NOT NULL,
                    `category` TEXT,
                    `is_archived` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_normalized_name` ON `exercises` (`normalized_name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_plans` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `workout_division_type` TEXT NOT NULL,
                    `workout_selection_mode` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `archived_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plans_is_active` ON `training_plans` (`is_active`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_templates` (
                    `id` TEXT NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `notes` TEXT,
                    `is_archived` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_templates_plan_id` ON `workout_templates` (`plan_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_templates_plan_id_code` ON `workout_templates` (`plan_id`, `code`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_templates_plan_id_position` ON `workout_templates` (`plan_id`, `position`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_exercise_templates` (
                    `id` TEXT NOT NULL,
                    `workout_template_id` TEXT NOT NULL,
                    `exercise_id` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `tracking_mode` TEXT NOT NULL,
                    `rest_between_sets_seconds` INTEGER NOT NULL,
                    `transition_rest_seconds` INTEGER NOT NULL,
                    `notes` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workout_template_id`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercise_templates_workout_template_id` ON `workout_exercise_templates` (`workout_template_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercise_templates_exercise_id` ON `workout_exercise_templates` (`exercise_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercise_templates_workout_template_id_position` ON `workout_exercise_templates` (`workout_template_id`, `position`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `set_templates` (
                    `id` TEXT NOT NULL,
                    `workout_exercise_template_id` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `minimum_repetitions` INTEGER,
                    `maximum_repetitions` INTEGER,
                    `target_repetitions` INTEGER,
                    `suggested_load_kg` REAL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workout_exercise_template_id`) REFERENCES `workout_exercise_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_templates_workout_exercise_template_id` ON `set_templates` (`workout_exercise_template_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_templates_workout_exercise_template_id_position` ON `set_templates` (`workout_exercise_template_id`, `position`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_rules` (
                    `id` TEXT NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `weekday` INTEGER NOT NULL,
                    `workout_template_id` TEXT,
                    `is_enabled` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`workout_template_id`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_rules_plan_id_weekday` ON `schedule_rules` (`plan_id`, `weekday`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_rules_workout_template_id` ON `schedule_rules` (`workout_template_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_days` (
                    `id` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `workout_template_id` TEXT,
                    `status` TEXT NOT NULL,
                    `session_id` TEXT,
                    `rescheduled_from_id` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`workout_template_id`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_days_date` ON `training_days` (`date`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_days_plan_id_date` ON `training_days` (`plan_id`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_days_workout_template_id` ON `training_days` (`workout_template_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_days_session_id` ON `training_days` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_days_rescheduled_from_id` ON `training_days` (`rescheduled_from_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_sessions` (
                    `id` TEXT NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `source_workout_template_id` TEXT,
                    `training_day_id` TEXT,
                    `workout_name_snapshot` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `started_at` INTEGER NOT NULL,
                    `finished_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`source_workout_template_id`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`training_day_id`) REFERENCES `training_days`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_plan_id` ON `workout_sessions` (`plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_source_workout_template_id` ON `workout_sessions` (`source_workout_template_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_training_day_id` ON `workout_sessions` (`training_day_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_status` ON `workout_sessions` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_started_at` ON `workout_sessions` (`started_at`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_exercises` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `source_exercise_id` TEXT,
                    `exercise_name_snapshot` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `tracking_mode_snapshot` TEXT NOT NULL,
                    `rest_between_sets_seconds_snapshot` INTEGER NOT NULL,
                    `transition_rest_seconds_snapshot` INTEGER NOT NULL,
                    `notes_snapshot` TEXT,
                    `status` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`session_id`) REFERENCES `workout_sessions`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`source_exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_session_id` ON `session_exercises` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_source_exercise_id` ON `session_exercises` (`source_exercise_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_session_id_position` ON `session_exercises` (`session_id`, `position`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_sets` (
                    `id` TEXT NOT NULL,
                    `session_exercise_id` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `planned_minimum_repetitions` INTEGER,
                    `planned_maximum_repetitions` INTEGER,
                    `actual_repetitions` INTEGER,
                    `actual_load_kg` REAL,
                    `status` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercises`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_sets_session_exercise_id` ON `session_sets` (`session_exercise_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_sets_session_exercise_id_position` ON `session_sets` (`session_exercise_id`, `position`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserve duplicate legacy active sessions as canceled history, then enforce
            // the invariant for all future writes at the database level.
            db.execSQL(
                """
                UPDATE workout_sessions
                SET status = 'CANCELED', finished_at = COALESCE(finished_at, updated_at)
                WHERE status = 'IN_PROGRESS'
                  AND id NOT IN (
                      SELECT id FROM workout_sessions
                      WHERE status = 'IN_PROGRESS'
                      ORDER BY started_at, id
                      LIMIT 1
                  )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_single_in_progress`
                ON `workout_sessions` (`status`)
                WHERE `status` = 'IN_PROGRESS'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `session_sets` ADD COLUMN `planned_target_repetitions` INTEGER")
            db.execSQL("ALTER TABLE `session_sets` ADD COLUMN `suggested_load_kg` REAL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_profile` (
                    `id` TEXT NOT NULL,
                    `display_name` TEXT,
                    `birth_date` INTEGER,
                    `height_cm` REAL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weight_entries` (
                    `id` TEXT NOT NULL,
                    `measured_at` INTEGER NOT NULL,
                    `weight_kg` REAL NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_entries_measured_at` ON `weight_entries` (`measured_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_entries_created_at` ON `weight_entries` (`created_at`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `hydration_settings` (
                    `id` TEXT NOT NULL,
                    `target_ml` INTEGER NOT NULL,
                    `target_mode` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            // Existing installs get an explicit empty profile and the documented
            // fallback. New installs seed the same rows in DatabaseModule.onCreate.
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT OR IGNORE INTO `user_profile` (`id`, `display_name`, `birth_date`, `height_cm`, `created_at`, `updated_at`) VALUES ('default', NULL, NULL, NULL, $now, $now)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `hydration_settings` (`id`, `target_ml`, `target_mode`, `created_at`, `updated_at`) VALUES ('default', 2000, 'DEFAULT_REFERENCE', $now, $now)",
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `session_intent` TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `session_origin` TEXT NOT NULL DEFAULT 'LEGACY'")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `advances_sequence` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `sequence_advance_id` TEXT")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plan_sequence_state` (
                    `plan_id` TEXT NOT NULL,
                    `next_workout_template_id` TEXT,
                    `cursor_index` INTEGER NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `last_advanced_session_id` TEXT,
                    `last_advanced_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`plan_id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(`next_workout_template_id`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_sequence_state_next_workout_template_id` ON `plan_sequence_state` (`next_workout_template_id`)")

            // Reconstruct a best-effort cursor for legacy plans. Only completed
            // normal sessions advance it; no historical schedule rows are changed.
            db.execSQL(
                """
                INSERT OR IGNORE INTO `plan_sequence_state` (
                    `plan_id`, `next_workout_template_id`, `cursor_index`, `revision`,
                    `last_advanced_session_id`, `last_advanced_at`, `created_at`, `updated_at`
                )
                SELECT
                    p.`id`,
                    NULL,
                    CASE
                        WHEN (
                            SELECT COUNT(*) FROM `workout_templates` wt_index
                            WHERE wt_index.`plan_id` = p.`id` AND wt_index.`is_archived` = 0
                        ) = 0 THEN 0
                        ELSE (
                            SELECT COUNT(*) FROM `workout_sessions` ws_index
                            WHERE ws_index.`plan_id` = p.`id`
                              AND ws_index.`status` = 'COMPLETED'
                              AND ws_index.`session_intent` = 'NORMAL'
                              AND ws_index.`advances_sequence` = 1
                        ) % (
                            SELECT COUNT(*) FROM `workout_templates` wt_index_count
                            WHERE wt_index_count.`plan_id` = p.`id` AND wt_index_count.`is_archived` = 0
                        )
                    END,
                    (
                        SELECT COUNT(*) FROM `workout_sessions` ws_revision
                        WHERE ws_revision.`plan_id` = p.`id`
                          AND ws_revision.`status` = 'COMPLETED'
                          AND ws_revision.`session_intent` = 'NORMAL'
                          AND ws_revision.`advances_sequence` = 1
                    ),
                    (
                        SELECT ws_last.`id`
                        FROM `workout_sessions` ws_last
                        WHERE ws_last.`plan_id` = p.`id`
                          AND ws_last.`status` = 'COMPLETED'
                          AND ws_last.`session_intent` = 'NORMAL'
                          AND ws_last.`advances_sequence` = 1
                        ORDER BY COALESCE(ws_last.`finished_at`, ws_last.`updated_at`) DESC, ws_last.`id` DESC
                        LIMIT 1
                    ),
                    (
                        SELECT COALESCE(ws_last_at.`finished_at`, ws_last_at.`updated_at`)
                        FROM `workout_sessions` ws_last_at
                        WHERE ws_last_at.`plan_id` = p.`id`
                          AND ws_last_at.`status` = 'COMPLETED'
                          AND ws_last_at.`session_intent` = 'NORMAL'
                          AND ws_last_at.`advances_sequence` = 1
                        ORDER BY COALESCE(ws_last_at.`finished_at`, ws_last_at.`updated_at`) DESC, ws_last_at.`id` DESC
                        LIMIT 1
                    ),
                    p.`created_at`,
                    p.`updated_at`
                FROM `training_plans` p
                """.trimIndent(),
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
