package com.smartworkalarm.data.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.CalendarContract
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
// This provider test does not render graphics; avoid native font ZIP initialization across SDK sandboxes.
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class CalendarProviderCancellationTest {
    @Test fun `production provider receives cancellation and closes a cursor returned late`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val provider = BlockingCalendarProvider()
        provider.attachInfo(context, ProviderInfo().apply { authority = CalendarContract.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val selection = object : CalendarSelectionRepository {
            override fun getSelectedCalendarIds() = Result.success(setOf(1L))
            override fun reconcile(availableCalendarIds: Set<Long>) = Result.success(setOf(1L))
            override fun setCalendarSelected(calendarId: Long, selected: Boolean) = Result.success(Unit)
            override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean) = Result.success(Unit)
        }
        val source = CalendarContractEventSource(context.contentResolver, selection)
        val from = Instant.parse("2026-10-05T15:00:00Z")
        val pending = async(Dispatchers.Default) { source.getEventsOverlapping(from, from.plusSeconds(86400)) }
        try {
            assertTrue(provider.started.await(5, TimeUnit.SECONDS))
            withTimeout(1_000) { pending.cancelAndJoin() }
            assertTrue(provider.cancelled.await(5, TimeUnit.SECONDS))
            assertEquals(1L, provider.closed.count)
        } finally {
            provider.unblock.countDown()
            assertTrue(provider.closed.await(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
        }
    }
}

private class BlockingCalendarProvider : ContentProvider() {
    val started = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val unblock = CountDownLatch(1)
    val closed = CountDownLatch(1)

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        error("Production query must use the CancellationSignal overload")

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?): Cursor {
        checkNotNull(cancellationSignal)
        if (uri == CalendarContract.Calendars.CONTENT_URI) {
            return MatrixCursor(arrayOf(CalendarContract.Calendars._ID)).apply { addRow(arrayOf(1L)) }
        }
        cancellationSignal.setOnCancelListener { cancelled.countDown() }
        started.countDown()
        while (unblock.count > 0) try { unblock.await() } catch (_: InterruptedException) { }
        return object : MatrixCursor(projection ?: emptyArray()) {
            override fun close() { super.close(); closed.countDown() }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
