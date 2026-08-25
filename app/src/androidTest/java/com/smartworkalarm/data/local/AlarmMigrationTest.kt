package com.smartworkalarm.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartworkalarm.di.MIGRATION_1_2
import com.smartworkalarm.di.MIGRATION_2_3
import com.smartworkalarm.di.MIGRATION_3_4
import com.smartworkalarm.di.MIGRATION_4_5
import com.smartworkalarm.di.MIGRATION_5_6
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

    @Test
    fun migrationFrom4To5AddsShiftAlarmTablesWithOptionalEmergencyType() {
        helper.createDatabase("migration-4-5", 4).close()

        val db = helper.runMigrationsAndValidate("migration-4-5", 5, true, MIGRATION_4_5)

        listOf("shift_alarm_groups", "shift_type_alarms", "shift_preview_states").forEach { tableName ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'").use { cursor ->
                assertEquals("Expected $tableName to exist", true, cursor.moveToFirst())
            }
        }
        db.query("PRAGMA table_info(shift_alarm_groups)").use { cursor ->
            var emergencyColumnIsNullable: Boolean? = null
            var draftDefault: String? = null
            while (cursor.moveToNext()) {
                when (cursor.getString(cursor.getColumnIndexOrThrow("name"))) {
                    "emergency_shift_type_id" -> {
                        emergencyColumnIsNullable = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 0
                    }
                    "is_draft" -> draftDefault = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                }
            }
            assertEquals(true, emergencyColumnIsNullable)
            assertEquals("0", draftDefault)
        }
        db.close()
    }

    @Test
    fun migrate5To6_defaultsBothSnoozeFlagsToEnabled() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO alarms (id,name,hour,minute,repeat_days_mask,enabled,skip_on_vacation," +
                    "calendar_keywords,ringtone_uri,skip_on_public_holiday,gradual_volume,snooze_minutes,created_at) " +
                    "VALUES (1,'아침',7,0,31,1,1,'휴가',NULL,0,0,9,123)",
            )
            execSQL("INSERT INTO shift_alarm_groups (id,name,enabled,emergency_shift_type_id,is_draft) VALUES (1,'교대',1,NULL,0)")
            execSQL(
                "INSERT INTO shift_type_alarms (id,group_id,name,exact_titles,hour,minute,repeat_days_mask,enabled," +
                    "ringtone_uri,gradual_volume,snooze_minutes,skip_on_public_holiday) " +
                    "VALUES (1,1,'야간','야간',20,0,127,1,NULL,0,11,0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT snooze_enabled FROM alarms WHERE id=1").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            db.query("SELECT snooze_enabled FROM shift_type_alarms WHERE id=1").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }

    companion object { private const val TEST_DB = "migration-test" }
}
