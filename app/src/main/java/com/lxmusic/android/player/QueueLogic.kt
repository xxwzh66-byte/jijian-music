package com.lxmusic.android.player

/**
 * Pure queue-manipulation helpers, extracted from [PlayerController] so the
 * index bookkeeping can be unit-tested without Android dependencies.
 */
object QueueLogic {

    /** Result of a reorder: the new list plus the index of the playing track. */
    data class ReorderResult<T>(val list: List<T>, val index: Int)

    /**
     * Moves the item at [from] to [to], keeping the item at [index] selected.
     * Returns the new list and the (possibly adjusted) index of that item.
     */
    fun <T> move(list: List<T>, index: Int, from: Int, to: Int): ReorderResult<T> {
        if (from !in list.indices || to !in list.indices || from == to) {
            return ReorderResult(list, index)
        }
        val mutable = list.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)

        val newIndex = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        return ReorderResult(mutable, newIndex)
    }

    /** Result of removing several queue entries at once. */
    data class RemoveManyResult<T>(
        val list: List<T>,
        val index: Int,
        /** The playing track itself was removed. */
        val playingRemoved: Boolean,
        /** Nothing matched, so the caller can skip its whole follow-up. */
        val changed: Boolean,
    )

    /**
     * V1.1.5: removes every entry satisfying [predicate], keeping [index] on the
     * same track when that track survives.
     *
     * Deleting a file has to take its queue slots with it. The single-entry
     * [remove] cannot express this: it stops at the first match and returns
     * `shouldPlay` for the whole call, but a deletion may match several slots
     * and what matters afterwards is only whether the *playing* one went away.
     */
    fun <T> removeAll(list: List<T>, index: Int, predicate: (T) -> Boolean): RemoveManyResult<T> {
        val playing = list.getOrNull(index)
        val kept = list.filterNot(predicate)
        if (kept.size == list.size) {
            return RemoveManyResult(list, index, playingRemoved = false, changed = false)
        }
        if (kept.isEmpty()) {
            return RemoveManyResult(emptyList(), -1, playingRemoved = true, changed = true)
        }
        val playingRemoved = playing != null && predicate(playing)
        val newIndex = if (playingRemoved) {
            // Land on the slot the survivor held, clamped into the new list.
            index.coerceAtMost(kept.size - 1)
        } else {
            // Same track, shifted by however many earlier entries disappeared.
            val before = list.take(index.coerceIn(0, list.size)).count(predicate)
            (index - before).coerceIn(0, kept.size - 1)
        }
        return RemoveManyResult(kept, newIndex, playingRemoved, changed = true)
    }

    /** Result of removing a queue entry. */
    data class RemoveResult<T>(val list: List<T>, val index: Int, val shouldPlay: Boolean)

    /**
     * Removes the entry at [removeAt]. [index] is the currently-playing index.
     * [shouldPlay] is true when the caller must (re)start playback of [index].
     */
    fun <T> remove(list: List<T>, index: Int, removeAt: Int): RemoveResult<T> {
        if (removeAt !in list.indices) return RemoveResult(list, index, false)
        val mutable = list.toMutableList()
        mutable.removeAt(removeAt)
        if (mutable.isEmpty()) return RemoveResult(emptyList(), -1, false)
        return when {
            removeAt < index -> RemoveResult(mutable, index - 1, false)
            removeAt > index -> RemoveResult(mutable, index, false)
            else -> {
                val newIndex = index.coerceAtMost(mutable.size - 1)
                RemoveResult(mutable, newIndex, true)
            }
        }
    }
}
