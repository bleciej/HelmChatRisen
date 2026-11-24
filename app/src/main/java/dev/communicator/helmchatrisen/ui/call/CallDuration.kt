package dev.communicator.helmchatrisen.ui.call

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Number of milliseconds in one second. */
private const val MILLIS_PER_SECOND = 1_000L

/** How often the visible call timer is refreshed. */
private const val TICK_INTERVAL_MS = 1_000L

/**
 * Formats an elapsed call duration for the call screen.
 *
 * Durations under one hour use `MM:SS`; longer calls use `H:MM:SS`. Negative or uninitialized
 * values are shown as zero so a fresh call never displays a misleading timer.
 *
 * @param elapsedMillis time since the first confirmed join, in milliseconds.
 * @return a locale-independent duration string suitable for a compact glove-friendly label.
 */
internal fun formatCallDuration(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Creates a cold flow that emits the current call elapsed time once per tick.
 *
 * [startedAtMillis] is an `elapsedRealtime()` timestamp owned by the call manager. A value of `0`
 * means the conference has not joined yet, so the flow emits only zero. Reconnects reuse the same
 * timestamp, which keeps the displayed duration continuous.
 *
 * @param startedAtMillis elapsed-realtime timestamp of the first confirmed join, or `0`.
 * @param nowMillis supplies the current elapsed-realtime value; defaults to `SystemClock.elapsedRealtime()`.
 * @param tickMs interval between consecutive emissions, in milliseconds.
 * @return a cold flow of elapsed milliseconds; a new collection recomputes the current value.
 */
internal fun callElapsedFlow(
    startedAtMillis: Long,
    nowMillis: () -> Long = { SystemClock.elapsedRealtime() },
    tickMs: Long = TICK_INTERVAL_MS,
): Flow<Long> = flow {
    if (startedAtMillis <= 0L) {
        emit(0L)
        return@flow
    }
    while (true) {
        emit((nowMillis() - startedAtMillis).coerceAtLeast(0L))
        delay(tickMs.toDuration(DurationUnit.MILLISECONDS))
    }
}

/**
 * Collects the current call elapsed time with the host lifecycle.
 *
 * Collection pauses while the host is below `STARTED`, so a stopped call screen or hidden
 * Picture-in-Picture window does not receive one-second wake-ups. When collection resumes, the
 * flow recomputes immediately from the same monotonic timestamp and never shows a stale value.
 *
 * Callers should read the returned state directly (for example with `by`) so their own
 * recomposition follows each tick; returning a plain `Long` from this helper would scope the state
 * read to the helper and leave the caller with a stale value.
 *
 * @param startedAtMillis elapsed-realtime timestamp of the first confirmed join, or `0`.
 * @return a state holding the elapsed milliseconds, updated once per active tick.
 */
@Composable
internal fun rememberCallElapsedMillis(startedAtMillis: Long): State<Long> {
    val elapsedFlow = remember(startedAtMillis) { callElapsedFlow(startedAtMillis) }
    return elapsedFlow.collectAsStateWithLifecycle(initialValue = 0L)
}
