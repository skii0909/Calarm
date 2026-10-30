package com.smartworkalarm.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.smartworkalarm.di.MIGRATION_6_7
import com.smartworkalarm.domain.model.Alarm
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlarmKeywordModePersistenceTest {
    @Test fun migrationKeepsExistingAlarmsInContainsMode() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE alarms (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO alarms VALUES (1, '출근')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            MIGRATION_6_7.migrate(db)
            db.query("SELECT name, calendar_keyword_exact_match FROM alarms WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("출근", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
        } finally { helper.close() }
    }

    @Test fun eachModeSurvivesEntityRoundTrip() {
        for (exact in listOf(false, true)) {
            val alarm = Alarm(1, "출근", 7, 0, 31, true, true, calendarKeywordExactMatch = exact)
            assertEquals(alarm, alarm.toEntity(123L).toDomain())
        }
    }
}
