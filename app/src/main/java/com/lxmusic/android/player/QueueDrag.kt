package com.lxmusic.android.player

import com.lxmusic.android.data.model.MusicInfo
import kotlin.math.roundToInt

/**
 * The numbers the queue screen puts in its header.
 *
 * Three separate questions, computed once from the queue instead of being
 * re-derived at each call site:
 *
 * - how many tracks are waiting (`remaining`),
 * - how long they will take (`remainingSeconds`),
 * - how far into the queue the current track sits (`position`).
 *
 * The subtlety is *what counts as remaining*. The current track is not
 * remaining — it is happening — but its full length should not be subtracted
 * either, because the part already played is gone. So the header uses
 * `total - played` where `played` is derived from the position inside the
 * current track, which makes the number shrink while a song plays rather than
 * jumping in whole-track steps.
 */
data class QueueSummary(
    val total: Int = 0,
    /** Tracks queued **after** the current one; the current one is not "remaining". */
    val remaining: Int = 0,
    /**
     * Seconds until the queue runs out, **including** what is left of the track
     * that is playing — that is the number a listener wants, and it shrinks
     * smoothly instead of dropping in whole-track steps.
     */
    val remainingSeconds: Int = 0,
    /** 1-based slot of the current track, or 0 when nothing is playing. */
    val position: Int = 0,
) {

    val isEmpty: Boolean get() = total == 0

    /**
     * "3/12 · 剩 34 分" — empty string when there is no queue.
     *
     * 不把「剩 N 首」和时长并排：首数不含当前曲，时长含当前曲的剩余部分，
     * 两个数并排就会出现「剩 1 首 · 5 分」这种自相矛盾的读法。首数由下方的
     * 行列表本身回答，头部只留"还要多久"。
     */
    fun headerText(): String {
        if (total == 0) return ""
        val head = if (position > 0) "$position/$total · " else "$total 首 · "
        return if (position == total && total > 0) {
            head + "最后一首"
        } else {
            head + "剩 ${durationText(remainingSeconds)}"
        }
    }

    companion object {

        /** "34 分" / "1 小时 12 分" — minute resolution is enough for a queue. */
        fun durationText(seconds: Int): String {
            if (seconds <= 0) return "0 分"
            val minutes = (seconds + 30) / 60
            if (minutes < 60) return "$minutes 分"
            val h = minutes / 60
            val m = minutes % 60
            return if (m == 0) "$h 小时" else "$h 小时 $m 分"
        }

        /**
         * Summarises [queue] with [index] playing and [positionMs] elapsed.
         *
         * [positionMs] only matters for the current track: it is clamped into
         * that track's own length so a stale position from the previous track
         * cannot eat into the next one's estimate.
         */
        fun of(
            queue: List<MusicInfo>,
            index: Int,
            positionMs: Long = 0L,
        ): QueueSummary {
            if (queue.isEmpty()) return QueueSummary()
            val current = queue.getOrNull(index)
            val remaining = if (current == null) 0 else queue.size - index - 1
            var seconds = 0
            if (current != null) {
                val len = current.intervalSeconds.toLong() * 1000L
                val elapsed = positionMs.coerceIn(0L, maxOf(len, 0L))
                seconds += ((len - elapsed) / 1000L).toInt().coerceAtLeast(0)
            }
            for (i in (index + 1).coerceAtLeast(0) until queue.size) {
                seconds += queue[i].intervalSeconds
            }
            return QueueSummary(
                total = queue.size,
                remaining = remaining,
                remainingSeconds = seconds,
                position = if (current == null) 0 else index + 1,
            )
        }
    }
}

/**
 * Where a dragged queue row would land.
 *
 * The queue screen lets a row be dragged by its handle. Two things have to be
 * decided on every pointer move: how far the row visually floats (so it follows
 * the finger) and which slot it would be dropped into. Both are pure arithmetic
 * over the accumulated drag distance, which is why they live here instead of
 * inside the gesture detector — a `detectDragGestures` block cannot be unit
 * tested, and off-by-one in the drop slot is exactly the kind of bug that only
 * shows up when someone reorders a fifty-track queue.
 */
object QueueDrag {

    /** A drag in progress: the target slot plus the float offset to draw at. */
    data class Drag(val from: Int, val to: Int, val offsetPx: Float) {
        val moved: Boolean get() = from != to
    }

    /**
     * Resolves a drag of [draggedPx] starting at [from] over a queue of [size]
     * rows [rowHeightPx] tall.
     *
     * The offset keeps the whole distance travelled (the row must stay under
     * the finger), while [to] is clamped into the queue. Callers pass the offset
     * to `graphicsLayer` and the target to the move function on release.
     *
     * A row is already "over" the next slot once it has travelled half a row,
     * so the divisor is `rowHeightPx` and the rounding is to nearest — flooring
     * instead would make every drag feel like it needs an extra half-row.
     */
    fun resolve(from: Int, draggedPx: Float, rowHeightPx: Float, size: Int): Drag {
        if (from !in 0 until size) return Drag(-1, -1, 0f)
        if (rowHeightPx <= 0f) return Drag(from, from, 0f)
        // 向上和向下必须一样灵敏。`roundToInt` 用的是 Math.round，对 -0.5
        // 取 0、对 +0.5 取 1，直接套用会让向上拖慢半行；这里按绝对值取整再
        // 补回符号，两个方向的判定点都在「半行」。
        val raw = draggedPx / rowHeightPx
        val steps = if (raw >= 0f) raw.roundToInt() else -(-raw).roundToInt()
        val to = (from + steps).coerceIn(0, size - 1)
        return Drag(from = from, to = to, offsetPx = draggedPx)
    }

    /**
     * The visual offset to draw a row at, given where it started and where the
     * drag currently points.
     *
     * This is what makes the list *look* like the drop will land where the
     * finger is: the dragged row follows the finger exactly, and every row it
     * has passed slides one slot the other way. Returning 0 for the dragged row
     * (as a naive implementation does) makes the drop slot invisible until the
     * finger is released.
     */
    fun displacementFor(rowIndex: Int, drag: Drag, rowHeightPx: Float): Float {
        if (!drag.moved || rowHeightPx <= 0f) {
            return if (rowIndex == drag.from) drag.offsetPx else 0f
        }
        return when {
            rowIndex == drag.from -> drag.offsetPx
            drag.to > drag.from && rowIndex in (drag.from + 1)..drag.to -> -rowHeightPx
            drag.to < drag.from && rowIndex in drag.to until drag.from -> rowHeightPx
            else -> 0f
        }
    }
}
