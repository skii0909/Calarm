package com.smartworkalarm.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {
    private lateinit var database: WorkAlarmDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkAlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alarmDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun crudPreservesVacationSettingAndSortsByTime() = runBlocking {
        val lateId = dao.upsert(entity(name = "늦은 알람", hour = 8, skipOnVacation = true))
        val earlyId = dao.upsert(entity(name = "이른 알람", hour = 7, skipOnVacation = false))

        assertEquals(listOf(earlyId, lateId), dao.observeAll().first().map { it.id })

        dao.setEnabled(lateId, false)
        val updated = dao.getById(lateId)!!
        assertFalse(updated.enabled)
        assertTrue(updated.skipOnCalendarMatch)

        dao.delete(earlyId)
        assertEquals(listOf(lateId), dao.observeAll().first().map { it.id })
    }

    private fun entity(name: String, hour: Int, skipOnVacation: Boolean) = AlarmEntity(
        name = name,
        hour = hour,
        minute = 0,
        repeatDaysMask = 31,
        enabled = true,
        skipOnCalendarMatch = skipOnVacation,
        createdAt = 1,
    )
}
