package dev.musicswitcher

/** Track order presets. The manual order is kept separately, so A-Z never overwrites it. */
internal enum class TrackSort(val key: String, val label: String) {
    Manual("manual", "Manual"),
    Name("name", "A–Z");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Manual
    }
}

/** Applies a stored newline-separated id order. Unknown items keep their relative order at the end. */
internal fun <T> applyStoredOrder(items: List<T>, stored: String?, id: (T) -> String): List<T> {
    if (stored == null) return items
    val ranks = stored.split('\n').withIndex().associate { it.value to it.index }
    return items.sortedBy { ranks[id(it)] ?: Int.MAX_VALUE }
}

internal fun <T> sortByName(items: List<T>, title: (T) -> String, directory: (T) -> String, id: (T) -> String): List<T> =
    items.sortedWith(
        compareBy<T, String>(String.CASE_INSENSITIVE_ORDER, title)
            .thenBy(title)
            .thenBy(String.CASE_INSENSITIVE_ORDER, directory)
            .thenBy(id),
    )

internal fun <T> arrangeTracks(
    items: List<T>, sort: TrackSort, manualOrder: String?,
    title: (T) -> String, directory: (T) -> String, id: (T) -> String,
): List<T> = when (sort) {
    TrackSort.Manual -> applyStoredOrder(items, manualOrder, id)
    TrackSort.Name -> sortByName(items, title, directory, id)
}
