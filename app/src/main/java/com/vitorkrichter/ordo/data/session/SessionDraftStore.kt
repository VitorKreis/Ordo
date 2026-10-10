package com.vitorkrichter.ordo.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SessionDraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SessionDraft? {
        val raw = preferences.getString(KEY_DRAFT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val drafts = json.getJSONObject("performanceDrafts")
            SessionDraft(
                workoutId = json.getString("workoutId"),
                completedSetKeys = json.getJSONArray("completedSetKeys").toStringSet(),
                performanceDrafts = drafts.keys().asSequence().associateWith { key ->
                    SessionPerformanceDraft(
                        repetitions = drafts.getJSONObject(key).optString("repetitions"),
                        loadKg = drafts.getJSONObject(key).optString("loadKg"),
                    )
                },
                lockedPerformanceKeys = json.optJSONArray("lockedPerformanceKeys")?.toStringSet().orEmpty(),
                exerciseOrder = json.optJSONArray("exerciseOrder")?.toStringList().orEmpty(),
                startedAtEpochMillis = json.optLong("startedAtEpochMillis").takeIf { it > 0L },
                lastSetCompletedAtEpochMillis = json.optLong("lastSetCompletedAtEpochMillis").takeIf { it > 0L },
                restTimerSetKey = json.optString("restTimerSetKey").takeIf(String::isNotBlank),
                restTimerEndsAtEpochMillis = json.optLong("restTimerEndsAtEpochMillis").takeIf { it > 0L },
                restTimerTotalSeconds = json.optInt("restTimerTotalSeconds").takeIf { it > 0 },
            )
        }.getOrNull()
    }

    fun save(draft: SessionDraft) {
        val performanceJson = JSONObject()
        draft.performanceDrafts.forEach { (key, value) ->
            performanceJson.put(
                key,
                JSONObject()
                    .put("repetitions", value.repetitions)
                    .put("loadKg", value.loadKg),
            )
        }
        val json = JSONObject()
            .put("workoutId", draft.workoutId)
            .put("completedSetKeys", JSONArray(draft.completedSetKeys.toList()))
            .put("performanceDrafts", performanceJson)
            .put("lockedPerformanceKeys", JSONArray(draft.lockedPerformanceKeys.toList()))
            .put("exerciseOrder", JSONArray(draft.exerciseOrder))
            .put("startedAtEpochMillis", draft.startedAtEpochMillis ?: 0L)
            .put("lastSetCompletedAtEpochMillis", draft.lastSetCompletedAtEpochMillis ?: 0L)
            .put("restTimerSetKey", draft.restTimerSetKey.orEmpty())
            .put("restTimerEndsAtEpochMillis", draft.restTimerEndsAtEpochMillis ?: 0L)
            .put("restTimerTotalSeconds", draft.restTimerTotalSeconds ?: 0)
        preferences.edit().putString(KEY_DRAFT, json.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_DRAFT).apply()
    }

    private fun JSONArray.toStringSet(): Set<String> {
        return toStringList().toSet()
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }
    }

    private companion object {
        const val PREFERENCES_NAME = "ordo_session_draft"
        const val KEY_DRAFT = "active_session_draft"
    }
}

data class SessionDraft(
    val workoutId: String,
    val completedSetKeys: Set<String>,
    val performanceDrafts: Map<String, SessionPerformanceDraft>,
    val lockedPerformanceKeys: Set<String>,
    val exerciseOrder: List<String>,
    val startedAtEpochMillis: Long?,
    val lastSetCompletedAtEpochMillis: Long?,
    val restTimerSetKey: String?,
    val restTimerEndsAtEpochMillis: Long?,
    val restTimerTotalSeconds: Int?,
)

data class SessionPerformanceDraft(
    val repetitions: String,
    val loadKg: String,
)
