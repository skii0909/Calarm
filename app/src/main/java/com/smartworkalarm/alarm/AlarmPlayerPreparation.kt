package com.smartworkalarm.alarm

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean

/** A cancelled preparation owns its player until platform setup returns, then disposes it. */
internal fun <T : Any> prepareAlarmPlayer(
    executor: Executor,
    dispatch: (() -> Unit) -> Unit,
    create: () -> T,
    configure: (T) -> Unit,
    dispose: (T) -> Unit,
    onReady: (T) -> Unit,
    onFailure: (Exception) -> Unit,
): () -> Unit {
    val active = AtomicBoolean(true)
    val task = FutureTask<Unit>({
        var candidate: T? = null
        try {
            val prepared = create()
            candidate = prepared
            configure(prepared)
            if (!active.get()) {
                dispose(prepared)
            } else {
                dispatch {
                    if (active.compareAndSet(true, false)) onReady(prepared) else dispose(prepared)
                }
            }
        } catch (error: Exception) {
            candidate?.let { runCatching { dispose(it) } }
            dispatch {
                if (active.compareAndSet(true, false)) onFailure(error)
            }
        }
    }, Unit)
    executor.execute(task)
    return {
        active.set(false)
        // Do not interrupt or release a MediaPlayer while its native/provider setup is running.
        task.cancel(false)
        (executor as? ThreadPoolExecutor)?.remove(task)
    }
}

/** Process-wide bounds prevent repeated alarms from accumulating stuck provider threads. */
internal object AlarmPreparationExecutors {
    private val external = Executors.newFixedThreadPool(3) { task ->
        Thread(task, "Calarm-external-sound").apply { isDaemon = true }
    }
    private val bundled = Executors.newFixedThreadPool(1) { task ->
        Thread(task, "Calarm-bundled-sound").apply { isDaemon = true }
    }

    fun forUri(uri: String): Executor =
        if (uri == AlarmSoundResolver.BUNDLED_FALLBACK) bundled else external
}
