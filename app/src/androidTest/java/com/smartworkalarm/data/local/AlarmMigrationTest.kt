package com.smartworkalarm.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartworkalarm.di.MIGRATION_1_2
import com.smartworkalarm.di.MIGRATION_2_3
import com.smartworkalarm.di.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkAlarmDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom1To2PreservesAlarmAndAddsDefaults() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO alarms (id,name,hour,minute,repeat_days_mask,enabled,skip_on_vacation,created_at) " +
                    "VALUES (8,'출근',7,0,31,1,1,123)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.query("SELECT * FROM alarms WHERE id = 8").use { cursor ->
            cursor.moveToFirst()
            assertEquals("출근", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
            assertEquals("휴가", cursor.getString(cursor.getColumnIndexOrThrow("calendar_keywords")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("ringtone_uri")))
        }
        db.close()
    }

    @Test
    fun migrationFrom2To3AddsPreviewStateTable() {
        helper.createDatabase("migration-2-3", 2).close()
        val db = helper.runMigrationsAndValidate("migration-2-3", 3, true, MIGRATION_2_3)
        db.execSQL(
            "INSERT INTO alarm_preview_states (alarm_id,decision,target_date,updated_at) " +
                "VALUES (8,'SKIP','2026-08-23',123)",
        )
        db.query("SELECT decision FROM alarm_preview_states WHERE alarm_id = 8").use { cursor ->
            cursor.moveToFirst()
            assertEquals("SKIP", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun migrationFrom3To4AddsAlarmControlDefaults() {
        helper.createDatabase("migration-3-4", 3).close()
        val db = helper.runMigrationsAndValidate("migration-3-4", 4, true, MIGRATION_3_4)
        db.query("PRAGMA table_info(alarms)").use { cursor ->
            val defaults = mutableMapOf<String, String?>()
            while (cursor.moveToNext()) {
                defaults[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            }
            assertEquals("0", defaults["skip_on_public_holiday"])
            assertEquals("0", defaults["gradual_volume"])
            assertEquals("5", defaults["snooze_minutes"])
        }
        db.close()
    }

    companion object { private const val TEST_DB = "migration-test" }
}
