package com.smartworkalarm.outlook

import android.content.Context
import androidx.room.Room
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OutlookStoreTest {
    @Test fun `room round trip preserves selection and cache without modifying alarm data`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, OutlookDatabase::class.java).build()
        try {
            val store = RoomOutlookStore(database)
            val initial = OutlookState(account = OutlookAccount("opaque/account", "person@example.com"),
                selectedIds = setOf("long/string/calendar=="), catalogLoaded = true,
                syncedAt = Instant.parse("2026-09-19T00:00:00Z"))
            store.update { initial }
            val restored = RoomOutlookStore(database).awaitLoaded()
            assertEquals(initial, restored)
            store.update { OutlookState() }
            assertNull(RoomOutlookStore(database).awaitLoaded().account)
        } finally { database.close() }
    }
}
