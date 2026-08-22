package com.smartworkalarm.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPreviewDaoTest {
    private lateinit var database: WorkAlarmDatabase
    private lateinit var dao: AlarmPreviewDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WorkAlarmDatabase::class.java,
        ).build()
        dao = database.alarmPreviewDao()
    }

    @After fun tearDown() = database.close()

    @Test fun previewStateCanBeReplacedAndDeleted() = runTest {
        dao.upsert(AlarmPreviewEntity(7, "SKIP", "2026-08-23", 1))
        dao.upsert(AlarmPreviewEntity(7, "RING", "2026-08-24", 2))

        assertEquals("RING", dao.get(7)?.decision)
        dao.delete(7)
        assertNull(dao.get(7))
    }
}
