package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    preferenceStore: PreferenceStore,
) {

    val downloadOnlyOverWifi: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    val saveChaptersAsCBZ: Preference<Boolean> = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    val splitTallImages: Preference<Boolean> = preferenceStore.getBoolean("split_tall_images", true)

    val autoDownloadWhileReading: Preference<Int> = preferenceStore.getInt("auto_download_while_reading", 0)

    val removeAfterReadSlots: Preference<Int> = preferenceStore.getInt("remove_after_read_slots", -1)

    val removeAfterMarkedAsRead: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    val removeBookmarkedChapters: Preference<Boolean> = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    val removeExcludeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapters: Preference<Boolean> = preferenceStore.getBoolean("download_new", false)

    val downloadNewChapterCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapterCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    val downloadNewUnreadChaptersOnly: Preference<Boolean> = preferenceStore.getBoolean(
        "download_new_unread_chapters_only",
        false,
    )

    val parallelSourceLimit: Preference<Int> = preferenceStore.getInt("download_parallel_source_limit", 5)

    val parallelPageLimit: Preference<Int> = preferenceStore.getInt("download_parallel_page_limit", 5)

    /**
     * Per-source overrides for download concurrency, bypassing [parallelSourceLimit] entirely.
     * Stored as one "sourceId:chapterConcurrency:pageConcurrency" entry per source,
     * e.g. "1234567890:10:10". A pageConcurrency of 0 means unlimited (all pages of a chapter
     * start downloading at once). Intended for self-hosted/unrestricted backends (e.g. a
     * personal Suwayomi server) where higher concurrency is safe but shouldn't apply to other
     * sources.
     */
    val sourceConcurrencyOverrides: Preference<Set<String>> = preferenceStore.getStringSet(
        "download_source_concurrency_overrides",
        emptySet(),
    )

    fun concurrencyOverridesById(): Map<Long, SourceConcurrencyOverride> {
        return sourceConcurrencyOverrides.get().mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.toLongOrNull()
            val chapters = parts.getOrNull(1)?.toIntOrNull()
            val pages = parts.getOrNull(2)?.toIntOrNull()
            if (id != null && chapters != null && pages != null && chapters > 0 && pages >= 0) {
                id to SourceConcurrencyOverride(chapters, pages)
            } else {
                null
            }
        }.toMap()
    }

    fun concurrencyOverrideFor(sourceId: Long): SourceConcurrencyOverride? {
        return concurrencyOverridesById()[sourceId]
    }

    fun setConcurrencyOverride(sourceId: Long, chapterConcurrency: Int, pageConcurrency: Int) {
        val updated = concurrencyOverridesById().toMutableMap()
        updated[sourceId] = SourceConcurrencyOverride(chapterConcurrency, pageConcurrency)
        sourceConcurrencyOverrides.set(updated.toEntrySet())
    }

    fun removeConcurrencyOverride(sourceId: Long) {
        val updated = concurrencyOverridesById().toMutableMap()
        updated.remove(sourceId)
        sourceConcurrencyOverrides.set(updated.toEntrySet())
    }

    private fun Map<Long, SourceConcurrencyOverride>.toEntrySet(): Set<String> {
        return map { (id, override) -> "$id:${override.chapterConcurrency}:${override.pageConcurrency}" }.toSet()
    }

    data class SourceConcurrencyOverride(val chapterConcurrency: Int, val pageConcurrency: Int)

    /**
     * Picks which downloads should be actively running from [bySourceId] (downloads grouped and
     * listed by their source id, completed ones already filtered out). Overridden sources bypass
     * [parallelSourceLimit] and may run [SourceConcurrencyOverride.chapterConcurrency] chapters
     * at once; every other source keeps the default one-chapter-at-a-time behavior, up to
     * [parallelSourceLimit] concurrent sources.
     */
    fun <T> selectActiveDownloads(bySourceId: List<Pair<Long, List<T>>>): List<T> {
        val parallelCount = parallelSourceLimit.get()
        val (overridden, normal) = bySourceId.partition { concurrencyOverrideFor(it.first) != null }

        return overridden.flatMap { (sourceId, downloads) ->
            downloads.take(concurrencyOverrideFor(sourceId)!!.chapterConcurrency)
        } + normal.take(parallelCount).map { (_, downloads) -> downloads.first() }
    }

    /** Page-download concurrency to use for [sourceId]; 0 from an override means unlimited. */
    fun pageConcurrencyFor(sourceId: Long): Int {
        return when (val override = concurrencyOverrideFor(sourceId)?.pageConcurrency) {
            null -> parallelPageLimit.get()
            0 -> Int.MAX_VALUE
            else -> override
        }
    }

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
