package com.smartworkalarm.alarm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlayerPreparationTest {
    @Test fun `timed out provider setup allows fallback without releasing player in use`() {
        val blockedWorker = Executors.newSingleThreadExecutor()
        val fallbackWorker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val main = LinkedBlockingQueue<() -> Unit>()
        val ready = mutableListOf<String>()
        try {
            val cancel = prepareAlarmPlayer(
                executor = blockedWorker,
                dispatch = { main.add(it); Unit },
                create = { "blocked" },
                configure = { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)) },
                dispose = { disposed.countDown() },
                onReady = { ready += it },
                onFailure = { throw AssertionError(it) },
            )
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            cancel()
            assertEquals("Cancellation must not release a player while setDataSource is running", 1L, disposed.count)
            prepareAlarmPlayer(
                executor = fallbackWorker,
                dispatch = { main.add(it); Unit },
                create = { "bundled" },
                configure = {}, dispose = {},
                onReady = { ready += it },
                onFailure = { throw AssertionError(it) },
            )
            main.poll(5, TimeUnit.SECONDS)!!.invoke()
            assertEquals(listOf("bundled"), ready)
            resume.countDown()
            assertTrue(disposed.await(5, TimeUnit.SECONDS))
            while (main.isNotEmpty()) main.poll()?.invoke()
            assertEquals(listOf("bundled"), ready)
        } finally {
            resume.countDown()
            blockedWorker.shutdownNow()
            fallbackWorker.shutdownNow()
        }
    }

    @Test fun `dismiss after setup before main delivery disposes late result`() {
        val main = mutableListOf<() -> Unit>()
        var disposed = false
        var played = false
        val cancel = prepareAlarmPlayer(
            executor = java.util.concurrent.Executor { it.run() },
            dispatch = { main += it },
            create = { "player" }, configure = {},
            dispose = { disposed = true },
            onReady = { played = true },
            onFailure = { throw AssertionError(it) },
        )
        cancel()
        main.single().invoke()
        assertTrue(disposed)
        assertEquals(false, played)
    }
}
